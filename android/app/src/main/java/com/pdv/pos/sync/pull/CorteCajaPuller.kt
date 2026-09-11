package com.pdv.pos.sync.pull

import com.pdv.pos.data.local.CajaDao
import com.pdv.pos.data.remote.CajaApiService
import com.pdv.pos.sync.SyncStateStore
import com.pdv.pos.sync.toEntity
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

// Cortes de caja creados en otras terminales de la misma sucursal (PLAN.md
// Parte 32, Grupo 3). Inmutables: insert-if-absent por localId correlacionado
// (local_id del backend si viene, si no su id).
@Singleton
class CorteCajaPuller @Inject constructor(
    private val api: CajaApiService,
    private val dao: CajaDao,
    private val syncStateStore: SyncStateStore,
) : EntityPuller {

    override val entidad = "cortes_caja"

    override suspend fun pull(sucursalId: String) {
        val cursor = syncStateStore.pullCursor(entidad)
        var pagina = 1
        var maxUpdated: Instant? = null
        while (true) {
            val respuesta = api.getCortes(
                sucursalId = sucursalId,
                page = pagina,
                pageSize = PULL_PAGE_SIZE,
                updatedSince = cursor,
            )
            for (item in respuesta.items) {
                val localId = item.localId ?: item.id
                if (dao.getCorteByLocalId(localId) == null) dao.insertCorte(item.toEntity(localId))
                val u = Instant.parse(item.updatedAt)
                if (maxUpdated == null || u.isAfter(maxUpdated)) maxUpdated = u
            }
            if (respuesta.items.isEmpty() || pagina.toLong() * PULL_PAGE_SIZE >= respuesta.total.toLong()) break
            pagina++
        }
        maxUpdated?.let { syncStateStore.setPullCursor(entidad, it.toString()) }
    }
}
