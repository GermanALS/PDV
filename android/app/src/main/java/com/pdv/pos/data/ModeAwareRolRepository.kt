package com.pdv.pos.data

import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.local.LocalRolRepository
import com.pdv.pos.data.remote.RemoteRolRepository
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.Rol
import com.pdv.pos.domain.repository.RolRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Resuelve local/remota segun BackendMode en DataStore, mismo criterio que
// ModeAwareUsuarioRepository (PLAN.md Parte 13, sub-paso 1).
@Singleton
class ModeAwareRolRepository @Inject constructor(
    private val local: LocalRolRepository,
    private val remote: RemoteRolRepository,
    private val preferences: ConfiguracionPreferences,
) : RolRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeRoles(): Flow<List<Rol>> =
        preferences.deviceConfig
            .map { it.backendMode }
            .distinctUntilChanged()
            .flatMapLatest { modo ->
                when (modo) {
                    BackendMode.LOCAL, BackendMode.LOCAL_CON_SINCRONIZACION -> local.observeRoles()
                    BackendMode.REMOTO -> remote.observeRoles()
                }
            }

    override suspend fun crearRol(rol: Rol, sucursalId: String, actorUsuario: String) {
        when (preferences.deviceConfig.first().backendMode) {
            BackendMode.LOCAL, BackendMode.LOCAL_CON_SINCRONIZACION -> local.crearRol(rol, sucursalId, actorUsuario)
            BackendMode.REMOTO -> remote.crearRol(rol, sucursalId, actorUsuario)
        }
    }

    override suspend fun actualizarRol(rol: Rol, sucursalId: String, actorUsuario: String) {
        when (preferences.deviceConfig.first().backendMode) {
            BackendMode.LOCAL, BackendMode.LOCAL_CON_SINCRONIZACION ->
                local.actualizarRol(rol, sucursalId, actorUsuario)
            BackendMode.REMOTO -> remote.actualizarRol(rol, sucursalId, actorUsuario)
        }
    }

    override suspend fun eliminarRol(rolId: String, sucursalId: String, actorUsuario: String) {
        when (preferences.deviceConfig.first().backendMode) {
            BackendMode.LOCAL, BackendMode.LOCAL_CON_SINCRONIZACION ->
                local.eliminarRol(rolId, sucursalId, actorUsuario)
            BackendMode.REMOTO -> remote.eliminarRol(rolId, sucursalId, actorUsuario)
        }
    }
}
