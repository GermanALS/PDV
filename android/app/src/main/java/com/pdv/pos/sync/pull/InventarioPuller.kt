package com.pdv.pos.sync.pull

import com.pdv.pos.data.local.ArticuloEntity
import com.pdv.pos.data.local.InventarioDao
import com.pdv.pos.data.local.InventarioEntity
import com.pdv.pos.data.local.catalogoDifiere
import com.pdv.pos.data.local.conCantidad
import com.pdv.pos.data.local.deltaStock
import com.pdv.pos.data.local.nuevoInventario
import com.pdv.pos.data.remote.InventarioApiService
import com.pdv.pos.data.remote.dto.InventarioItemDto
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import com.pdv.pos.sync.EventoAditivoSyncEngine
import com.pdv.pos.sync.SyncStateStore
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// Merge del stock de la sucursal seleccionada (PLAN.md Parte 32, Grupo 3):
//  - fila local ausente -> insert (isSynced = true)
//  - sin movimientos pendientes de subir (PLAN.md Parte 34, A-1(b)) ->
//    overwrite (no hay delta local que perder)
//  - con movimientos pendientes -> se conserva lo local; si el valor remoto
//    ademas difiere, se registra el cruce en sync_conflicts (D4)
//
// Reconciliacion unica por sucursal (Parte 34): mientras no este marcada, el
// pull baja todo el inventario (las filas que ya divergian tienen updated_at
// viejo y el cursor no las veria) y una fila con pendientes queda en
// remoto + efecto de sus pendientes en vez de "gana lo local". Al terminar
// deja un unico registro resumen en sync_conflicts.
//
// Catalogo (PLAN.md Parte 35): cada item trae los atributos del articulo. Sin
// edicion local pendiente (en ninguna sucursal) gana el remoto; con una se
// conserva lo local y el LWW lo decide el push. La ubicacion sigue la misma
// regla por fila. Un articulo remoto desconocido se crea (o se enlaza por SKU
// con uno local aun sin subir). Una segunda reconciliacion unica de catalogo
// baja todo una vez para los dispositivos con cursor ya avanzado.
@Singleton
class InventarioPuller @Inject constructor(
    private val api: InventarioApiService,
    private val dao: InventarioDao,
    private val syncStateStore: SyncStateStore,
    private val eventoAditivoSyncEngine: EventoAditivoSyncEngine,
    private val appLogger: AppLogger,
) : EntityPuller {

    override val entidad = "inventario"

    override suspend fun pull(sucursalId: String) {
        val reconciliar = !syncStateStore.inventarioReconciliado(sucursalId)
        val reconciliarCatalogo = !syncStateStore.catalogoReconciliado(sucursalId)
        val desde = if (reconciliar || reconciliarCatalogo) null else syncStateStore.pullDesde(entidad, sucursalId)
        var pagina = 1
        var corregidas = 0
        var maxUpdated: Instant? = null
        while (true) {
            val respuesta = api.getInventario(
                sucursalId = sucursalId,
                q = null,
                page = pagina,
                pageSize = PULL_PAGE_SIZE,
                updatedSince = desde,
            )
            for (item in respuesta.items) {
                if (mergeItem(sucursalId, item, reconciliar)) corregidas++
                val u = Instant.parse(item.updatedAt)
                if (maxUpdated == null || u.isAfter(maxUpdated)) maxUpdated = u
            }
            if (respuesta.items.isEmpty() || pagina.toLong() * PULL_PAGE_SIZE >= respuesta.total.toLong()) break
            pagina++
        }
        maxUpdated?.let { syncStateStore.setPullCursor(entidad, sucursalId, it.toString()) }
        if (reconciliar) {
            if (corregidas > 0) eventoAditivoSyncEngine.registrarReconciliacionInventario(sucursalId, "system", corregidas)
            syncStateStore.marcarInventarioReconciliado(sucursalId)
        }
        if (reconciliarCatalogo) syncStateStore.marcarCatalogoReconciliado(sucursalId)
    }

    // Devuelve true si la cantidad local quedo distinta de la que tenia.
    private suspend fun mergeItem(sucursalId: String, item: InventarioItemDto, reconciliar: Boolean): Boolean {
        val now = System.currentTimeMillis()
        val articulo = resolverArticulo(sucursalId, item, now)
        val remoteCantidad = BigDecimal(item.cantidad)
        val local = dao.getInventario(sucursalId, articulo.localId)
        if (local == null) {
            dao.insertInventario(
                nuevoInventario(sucursalId, articulo.localId, remoteCantidad, item.ubicacion, now).copy(isSynced = true),
            )
            return false
        }

        val pendientes = dao.getMovimientosPendientes(sucursalId, articulo.localId)
        // La ubicacion solo la protege una edicion local pendiente, no los
        // movimientos de stock pendientes.
        val ubicacion = if (local.edicionPendiente) local.ubicacion else item.ubicacion
        return when {
            pendientes.isEmpty() -> {
                dao.updateInventario(
                    local.conCantidad(remoteCantidad, now).copy(isSynced = true, ubicacion = ubicacion),
                )
                local.difiere(remoteCantidad)
            }

            reconciliar -> {
                val objetivo = pendientes.fold(remoteCantidad) { acc, mov -> acc + mov.deltaStock() }
                dao.updateInventario(local.conCantidad(objetivo, now).copy(ubicacion = ubicacion))
                local.difiere(objetivo)
            }

            else -> {
                if (local.ubicacion != ubicacion) dao.updateInventario(local.copy(ubicacion = ubicacion))
                if (local.difiere(remoteCantidad)) {
                    eventoAditivoSyncEngine.registrarDivergenciaLocalGana(
                        entidad = "inventario",
                        entidadLocalId = local.localId,
                        sucursalId = sucursalId,
                        usuario = "system",
                        valorLocal = local.cantidad,
                        valorRemoto = remoteCantidad,
                    )
                }
                false
            }
        }
    }

    // Devuelve el articulo local del item, creandolo o enlazandolo si no
    // existe, y deja sus atributos al dia salvo que haya una edicion pendiente.
    private suspend fun resolverArticulo(sucursalId: String, item: InventarioItemDto, now: Long): ArticuloEntity {
        val conocido = dao.getArticuloByRemoteId(item.articuloId)
        if (conocido == null) {
            val sinRemoto = dao.getArticuloSinRemotoPorSku(item.sku)
            if (sinRemoto != null) {
                val enlazado = sinRemoto.copy(remoteId = item.articuloId)
                dao.updateArticulo(enlazado)
                appLogger.log(
                    LogType.WARN,
                    sucursalId = sucursalId,
                    usuario = "system",
                    mensaje = "Pull inventario: articulo local ${sinRemoto.localId} (sku ${item.sku}) enlazado al remoto ${item.articuloId}",
                )
                return enlazado
            }
            val nuevo = ArticuloEntity(
                localId = UUID.randomUUID().toString(),
                remoteId = item.articuloId,
                codigoBarras = item.codigoBarras,
                sku = item.sku,
                nombre = item.nombre,
                descripcion = item.descripcion,
                categoria = item.categoria,
                unidadMedida = item.unidadMedida,
                precioVenta = BigDecimal(item.precioVenta),
                costo = item.costo?.let(::BigDecimal),
                activo = true,
                updatedAt = now,
                isSynced = true,
                deletedAt = null,
            )
            dao.insertArticulo(nuevo)
            return nuevo
        }
        if (dao.hayEdicionPendiente(conocido.localId)) return conocido
        val remoto = conocido.copy(
            codigoBarras = item.codigoBarras,
            nombre = item.nombre,
            descripcion = item.descripcion,
            categoria = item.categoria,
            unidadMedida = item.unidadMedida,
            precioVenta = BigDecimal(item.precioVenta),
            costo = item.costo?.let(::BigDecimal),
        )
        if (!conocido.catalogoDifiere(remoto)) return conocido
        val actualizado = remoto.copy(isSynced = true)
        dao.updateArticulo(actualizado)
        return actualizado
    }

    private fun InventarioEntity.difiere(otra: BigDecimal) = cantidad.compareTo(otra) != 0
}
