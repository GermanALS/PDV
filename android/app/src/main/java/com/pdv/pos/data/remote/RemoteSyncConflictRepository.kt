package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.SyncConflictCreateRequestDto
import com.pdv.pos.data.remote.dto.SyncConflictDto
import com.pdv.pos.domain.model.SyncConflict
import com.pdv.pos.domain.repository.SyncConflictRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

// Las fallas de red se propagan por el Flow (sin capturarlas aca), igual
// que RemoteSucursalRepository (PLAN.md Parte 6).
@Singleton
class RemoteSyncConflictRepository @Inject constructor(
    private val api: SyncConflictApiService,
) : SyncConflictRepository {

    override fun observeConflictos(): Flow<List<SyncConflict>> = flow {
        emit(api.getSyncConflicts().items.map { it.toDomain() })
    }

    /**
     * Push dispositivo -> backend del motor de sync
     * (docs/api-contract.md 3.2). Idempotente del lado del backend:
     * reintentar con el mismo `id` no duplica ni sobreescribe. No forma
     * parte de `SyncConflictRepository` porque el panel de la Parte 19 es
     * de solo lectura; lo consume el loop de sincronización remoto.
     */
    suspend fun subirConflicto(conflicto: SyncConflict) {
        api.createSyncConflict(conflicto.toCreateRequestDto())
    }
}

private fun SyncConflictDto.toDomain() = SyncConflict(
    id = id,
    entidad = entidad,
    entidadLocalId = entidadLocalId,
    sucursalId = sucursalId,
    valorLocal = valorLocal.toString(),
    valorRemoto = valorRemoto.toString(),
    valorResuelto = valorResuelto.toString(),
    politicaAplicada = politicaAplicada,
    resueltoAutomaticamente = resueltoAutomaticamente,
    fechaDeteccion = Instant.parse(fechaDeteccion).toEpochMilli(),
)

private fun SyncConflict.toCreateRequestDto() = SyncConflictCreateRequestDto(
    id = id,
    entidad = entidad,
    entidadLocalId = entidadLocalId,
    sucursalId = sucursalId,
    valorLocal = Json.parseToJsonElement(valorLocal),
    valorRemoto = Json.parseToJsonElement(valorRemoto),
    valorResuelto = Json.parseToJsonElement(valorResuelto),
    politicaAplicada = politicaAplicada,
    resueltoAutomaticamente = resueltoAutomaticamente,
    fechaDeteccion = Instant.ofEpochMilli(fechaDeteccion).toString(),
)
