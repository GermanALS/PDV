package com.pdv.pos.sync.pull

import com.pdv.pos.data.local.RetiroDao
import com.pdv.pos.data.remote.RetiroApiService
import com.pdv.pos.sync.SyncStateStore
import com.pdv.pos.sync.toEntity
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

// Retiros de efectivo creados en otras terminales de la misma sucursal
// (PLAN.md Parte 32, Grupo 3). Ver CorteCajaPuller: mismo insert-if-absent.
@Singleton
class RetiroPuller @Inject constructor(
    private val api: RetiroApiService,
    private val dao: RetiroDao,
    private val syncStateStore: SyncStateStore,
) : EntityPuller {

    override val entidad = "retiros_efectivo"

    override suspend fun pull(sucursalId: String) {
        val cursor = syncStateStore.pullCursor(entidad)
        var pagina = 1
        var maxUpdated: Instant? = null
        while (true) {
            val respuesta = api.getRetiros(
                sucursalId = sucursalId,
                page = pagina,
                pageSize = PULL_PAGE_SIZE,
                updatedSince = cursor,
            )
            for (item in respuesta.items) {
                val localId = item.localId ?: item.id
                if (dao.getRetiroByLocalId(localId) == null) dao.insertRetiro(item.toEntity(localId))
                val u = Instant.parse(item.updatedAt)
                if (maxUpdated == null || u.isAfter(maxUpdated)) maxUpdated = u
            }
            if (respuesta.items.isEmpty() || pagina.toLong() * PULL_PAGE_SIZE >= respuesta.total.toLong()) break
            pagina++
        }
        maxUpdated?.let { syncStateStore.setPullCursor(entidad, it.toString()) }
    }
}
