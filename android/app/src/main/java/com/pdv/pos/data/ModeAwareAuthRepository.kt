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
            BackendMode.LOCAL, BackendMode.LOCAL_CON_SINCRONIZACION -> local.login(username, password)
            BackendMode.REMOTO -> remote.login(username, password)
        }
    }
}
