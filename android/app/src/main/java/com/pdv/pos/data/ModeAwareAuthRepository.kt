package com.pdv.pos.data

import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.local.LocalAuthRepository
import com.pdv.pos.data.remote.RemoteAuthRepository
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.repository.AuthRepository
import com.pdv.pos.domain.repository.LoginResultado
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

// Resuelve local/remota segun BackendMode en DataStore, mismo criterio que
// ModeAwareUsuarioRepository/ModeAwareRolRepository (PLAN.md Parte 13,
// Decision 1).
@Singleton
class ModeAwareAuthRepository @Inject constructor(
    private val local: LocalAuthRepository,
    private val remote: RemoteAuthRepository,
    private val preferences: ConfiguracionPreferences,
) : AuthRepository {

    override suspend fun login(username: String, password: String): LoginResultado {
        return when (preferences.deviceConfig.first().backendMode) {
            BackendMode.LOCAL -> local.login(username, password)
            BackendMode.LOCAL_CON_SINCRONIZACION -> loginLocalConTokenRemoto(username, password)
            BackendMode.REMOTO -> remote.login(username, password)
        }
    }

    // El login es contra los datos locales (la app opera offline), pero si el
    // backend es alcanzable se hace tambien un login remoto best-effort para
    // quedarse con el JWT que el SyncWorker necesita (PLAN.md Parte 32, D3).
    // Cualquier fallo remoto (red caida, credenciales que no existen en el
    // backend, 5xx) no afecta al login local: se sigue con una sesion sin
    // token y el push queda pendiente hasta el proximo login con el backend
    // alcanzable. La identidad de la sesion (usuario/rol) es la local; el
    // token solo se usa para autenticar las llamadas HTTP del worker, que el
    // backend valida por su propio `sub`.
    private suspend fun loginLocalConTokenRemoto(username: String, password: String): LoginResultado {
        val localResultado = local.login(username, password)
        if (localResultado !is LoginResultado.Exitoso) return localResultado

        val remoto = runCatching { remote.login(username, password) }
            .getOrNull() as? LoginResultado.Exitoso
            ?: return localResultado

        // Si el backend exige el cambio aunque el usuario local no, la sesion
        // tambien lo exige: con la marca remota encendida el JWT recibe 403 en
        // todas las rutas (PLAN.md Parte 37, A-3).
        val debeCambiar = localResultado.usuario.debeCambiarPassword || remoto.usuario.debeCambiarPassword
        return localResultado.copy(
            usuario = localResultado.usuario.copy(debeCambiarPassword = debeCambiar),
            accessToken = remoto.accessToken,
        )
    }

    override suspend fun cambiarPassword(usuarioId: String, nuevaPassword: String) {
        when (preferences.deviceConfig.first().backendMode) {
            BackendMode.LOCAL -> local.cambiarPassword(usuarioId, nuevaPassword)
            BackendMode.LOCAL_CON_SINCRONIZACION -> cambiarLocalYRemoto(usuarioId, nuevaPassword)
            BackendMode.REMOTO -> remote.cambiarPassword(usuarioId, nuevaPassword)
        }
    }

    // Mismo criterio que el login: la contrasena local es la que decide, y la
    // remota se cambia best-effort para que el proximo login siga obteniendo
    // el JWT del SyncWorker con la misma contrasena. Sin token en la sesion la
    // llamada remota recibe 401 y se ignora.
    private suspend fun cambiarLocalYRemoto(usuarioId: String, nuevaPassword: String) {
        local.cambiarPassword(usuarioId, nuevaPassword)
        runCatching { remote.cambiarPassword(usuarioId, nuevaPassword) }
    }
}
