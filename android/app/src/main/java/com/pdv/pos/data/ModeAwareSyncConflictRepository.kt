package com.pdv.pos.data

import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.local.LocalSyncConflictRepository
import com.pdv.pos.data.remote.RemoteSyncConflictRepository
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.SyncConflict
import com.pdv.pos.domain.repository.SyncConflictRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Resuelve local/remota segun BackendMode en DataStore (PLAN.md Parte 19,
// sub-paso 4; CLAUDE.md sec. 3). Mismo split que ModeAwareVentaRepository:
// en LOCAL y LOCAL_CON_SINCRONIZACION el motor de sync escribe los
// conflictos en Room, asi que ahi vive la auditoria de este dispositivo;
// en REMOTO no hay Room y los conflictos consolidados los sirve el backend.
@Singleton
class ModeAwareSyncConflictRepository @Inject constructor(
    private val local: LocalSyncConflictRepository,
    private val remote: RemoteSyncConflictRepository,
    private val preferences: ConfiguracionPreferences,
) : SyncConflictRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeConflictos(): Flow<List<SyncConflict>> =
        preferences.deviceConfig
            .map { it.backendMode }
            .distinctUntilChanged()
            .flatMapLatest { modo ->
                when (modo) {
                    BackendMode.LOCAL, BackendMode.LOCAL_CON_SINCRONIZACION -> local.observeConflictos()
                    BackendMode.REMOTO -> remote.observeConflictos()
                }
            }
}
