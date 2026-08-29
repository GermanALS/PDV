package com.pdv.pos.data.local

import com.pdv.pos.domain.model.SyncConflict
import com.pdv.pos.domain.repository.SyncConflictRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Solo lectura sobre el SyncConflictDao ya existente desde la Parte 6
// (PLAN.md Parte 19). El orden (mas reciente primero) lo fija la @Query.
@Singleton
class LocalSyncConflictRepository @Inject constructor(
    private val dao: SyncConflictDao,
) : SyncConflictRepository {

    override fun observeConflictos(): Flow<List<SyncConflict>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }
}

private fun SyncConflictEntity.toDomain() = SyncConflict(
    id = id,
    entidad = entidad,
    entidadLocalId = entidadLocalId,
    sucursalId = sucursalId,
    valorLocal = valorLocal,
    valorRemoto = valorRemoto,
    valorResuelto = valorResuelto,
    politicaAplicada = politicaAplicada,
    resueltoAutomaticamente = resueltoAutomaticamente,
    fechaDeteccion = fechaDeteccion,
)
