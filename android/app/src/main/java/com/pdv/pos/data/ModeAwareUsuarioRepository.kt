package com.pdv.pos.data

import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.local.LocalUsuarioRepository
import com.pdv.pos.data.remote.RemoteUsuarioRepository
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.Usuario
import com.pdv.pos.domain.repository.UsuarioRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Resuelve local/remota segun BackendMode en DataStore, mismo criterio que
// ModeAwareSucursalRepository (PLAN.md Parte 6, sub-paso 5): "local con
// sincronizacion" lee/escribe en Room igual que "local".
@Singleton
class ModeAwareUsuarioRepository @Inject constructor(
    private val local: LocalUsuarioRepository,
    private val remote: RemoteUsuarioRepository,
    private val preferences: ConfiguracionPreferences,
) : UsuarioRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeUsuarios(): Flow<List<Usuario>> =
        preferences.deviceConfig
            .map { it.backendMode }
            .distinctUntilChanged()
            .flatMapLatest { modo ->
                when (modo) {
                    BackendMode.LOCAL, BackendMode.LOCAL_CON_SINCRONIZACION -> local.observeUsuarios()
                    BackendMode.REMOTO -> remote.observeUsuarios()
                }
            }

    override suspend fun crearUsuario(usuario: Usuario, passwordHash: String?, sucursalId: String, actorUsuario: String) {
        when (preferences.deviceConfig.first().backendMode) {
            BackendMode.LOCAL, BackendMode.LOCAL_CON_SINCRONIZACION ->
                local.crearUsuario(usuario, passwordHash, sucursalId, actorUsuario)
            BackendMode.REMOTO -> remote.crearUsuario(usuario, passwordHash, sucursalId, actorUsuario)
        }
    }

    override suspend fun actualizarUsuario(usuario: Usuario, passwordHash: String?, sucursalId: String, actorUsuario: String) {
        when (preferences.deviceConfig.first().backendMode) {
            BackendMode.LOCAL, BackendMode.LOCAL_CON_SINCRONIZACION ->
                local.actualizarUsuario(usuario, passwordHash, sucursalId, actorUsuario)
            BackendMode.REMOTO -> remote.actualizarUsuario(usuario, passwordHash, sucursalId, actorUsuario)
        }
    }

    override suspend fun eliminarUsuario(usuarioId: String, sucursalId: String, actorUsuario: String) {
        when (preferences.deviceConfig.first().backendMode) {
            BackendMode.LOCAL, BackendMode.LOCAL_CON_SINCRONIZACION ->
                local.eliminarUsuario(usuarioId, sucursalId, actorUsuario)
            BackendMode.REMOTO -> remote.eliminarUsuario(usuarioId, sucursalId, actorUsuario)
        }
    }
}
