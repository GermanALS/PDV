package com.pdv.pos.sync.pull

import com.pdv.pos.data.local.InventarioDao
import com.pdv.pos.data.local.InventarioEntity
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
        val desde = if (reconciliar) null else syncStateStore.pullDesde(entidad, sucursalId)
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
    }

    // Devuelve true si la cantidad local quedo distinta de la que tenia.
    private suspend fun mergeItem(sucursalId: String, item: InventarioItemDto, reconciliar: Boolean): Boolean {
        val articulo = dao.getArticuloByRemoteId(item.articuloId)
        if (articulo == null) {
            appLogger.log(
                LogType.WARN,
                sucursalId = sucursalId,
                usuario = "system",
                mensaje = "Pull inventario: articulo remoto ${item.articuloId} desconocido localmente, se omite",
            )
            return false
        }
        val now = System.currentTimeMillis()
        val remoteCantidad = BigDecimal(item.cantidad)
        val local = dao.getInventario(sucursalId, articulo.localId)
        if (local == null) {
            dao.insertInventario(
                nuevoInventario(sucursalId, articulo.localId, remoteCantidad, item.ubicacion, now).copy(isSynced = true),
            )
            return false
        }

        val pendientes = dao.getMovimientosPendientes(sucursalId, articulo.localId)
        return when {
            pendientes.isEmpty() -> {
                dao.updateInventario(
                    local.conCantidad(remoteCantidad, now).copy(isSynced = true, ubicacion = item.ubicacion),
                )
                local.difiere(remoteCantidad)
            }

            reconciliar -> {
                val objetivo = pendientes.fold(remoteCantidad) { acc, mov -> acc + mov.deltaStock() }
                dao.updateInventario(local.conCantidad(objetivo, now))
                local.difiere(objetivo)
            }

            else -> {
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

    private fun InventarioEntity.difiere(otra: BigDecimal) = cantidad.compareTo(otra) != 0
}
