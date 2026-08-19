package com.pdv.pos.data

import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.local.LocalSucursalRepository
import com.pdv.pos.data.remote.RemoteSucursalRepository
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.Sucursal
import com.pdv.pos.domain.repository.SucursalRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Resuelve local/remota segun BackendMode en DataStore (PLAN.md Parte 6,
// sub-paso 5 "Wiring"; CLAUDE.md sec. 3). "Local con sincronizacion" lee
// de Room igual que "local" - el disparo del sync en background hacia el
// remoto es infraestructura aparte, fuera del alcance de este sub-paso.
@Singleton
class ModeAwareSucursalRepository @Inject constructor(
    private val local: LocalSucursalRepository,
    private val remote: RemoteSucursalRepository,
    private val preferences: ConfiguracionPreferences,
) : SucursalRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSucursales(): Flow<List<Sucursal>> =
        preferences.deviceConfig
            .map { it.backendMode }
            .distinctUntilChanged()
            .flatMapLatest { modo ->
                when (modo) {
                    BackendMode.LOCAL, BackendMode.LOCAL_CON_SINCRONIZACION -> local.observeSucursales()
                    BackendMode.REMOTO -> remote.observeSucursales()
                }
            }
}
