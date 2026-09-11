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

        val tokenRemoto = runCatching { remote.login(username, password) }
            .getOrNull()
            .let { it as? LoginResultado.Exitoso }
            ?.accessToken

        return if (tokenRemoto != null) localResultado.copy(accessToken = tokenRemoto) else localResultado
    }
}
