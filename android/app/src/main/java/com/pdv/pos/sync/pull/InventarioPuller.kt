package com.pdv.pos.sync.pull

import com.pdv.pos.data.local.InventarioDao
import com.pdv.pos.data.local.conCantidad
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
//  - fila local ausente     -> insert (isSynced = true)
//  - fila local sincronizada -> overwrite (no hay delta local que perder)
//  - fila local sin subir    -> se conserva lo local; si el valor remoto
//    ademas difiere, se registra el cruce en sync_conflicts (D4)
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
        val cursor = syncStateStore.pullCursor(entidad)
        var pagina = 1
        var maxUpdated: Instant? = null
        while (true) {
            val respuesta = api.getInventario(
                sucursalId = sucursalId,
                q = null,
                page = pagina,
                pageSize = PULL_PAGE_SIZE,
                updatedSince = cursor,
            )
            for (item in respuesta.items) {
                mergeItem(sucursalId, item)
                val u = Instant.parse(item.updatedAt)
                if (maxUpdated == null || u.isAfter(maxUpdated)) maxUpdated = u
            }
            if (respuesta.items.isEmpty() || pagina.toLong() * PULL_PAGE_SIZE >= respuesta.total.toLong()) break
            pagina++
        }
        maxUpdated?.let { syncStateStore.setPullCursor(entidad, it.toString()) }
    }

    private suspend fun mergeItem(sucursalId: String, item: InventarioItemDto) {
        val articulo = dao.getArticuloByRemoteId(item.articuloId)
        if (articulo == null) {
            appLogger.log(
                LogType.WARN,
                sucursalId = sucursalId,
                usuario = "system",
                mensaje = "Pull inventario: articulo remoto ${item.articuloId} desconocido localmente, se omite",
            )
            return
        }
        val now = System.currentTimeMillis()
        val remoteCantidad = BigDecimal(item.cantidad)
        val local = dao.getInventario(sucursalId, articulo.localId)
        when {
            local == null -> dao.insertInventario(
                nuevoInventario(sucursalId, articulo.localId, remoteCantidad, item.ubicacion, now).copy(isSynced = true),
            )

            local.isSynced -> dao.updateInventario(
                local.conCantidad(remoteCantidad, now).copy(isSynced = true, ubicacion = item.ubicacion),
            )

            local.cantidad.compareTo(remoteCantidad) != 0 -> eventoAditivoSyncEngine.registrarDivergenciaLocalGana(
                entidad = "inventario",
                entidadLocalId = local.localId,
                sucursalId = sucursalId,
                usuario = "system",
                valorLocal = local.cantidad,
                valorRemoto = remoteCantidad,
            )
        }
    }
}
