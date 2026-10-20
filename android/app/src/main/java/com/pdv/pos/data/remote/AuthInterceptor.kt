package com.pdv.pos.data.remote

import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.domain.model.BackendMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

// Adjunta el JWT de la sesion remota a cada request (api-contract.md sec.
// 12) y reacciona a un 401 del backend cerrando la sesion: MainActivity
// observa SessionManager.session y vuelve a LoginScreen cuando pasa a null.
// Solo en modo REMOTO un 401 cierra la sesion: en LOCAL_CON_SINCRONIZACION
// la sesion es local (el JWT solo autentica las llamadas al backend, sobre
// todo las del SyncWorker), asi que un 401
// ahi lo registra el orquestador como "sync requiere re-login" sin expulsar
// al cajero (PLAN.md Parte 36, M-5). El modo se cachea igual que en
// DynamicHostInterceptor (PLAN.md Parte 29, B-5).
@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager,
    preferences: ConfiguracionPreferences,
) : Interceptor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var modo: BackendMode = runBlocking { preferences.deviceConfig.first().backendMode }

    init {
        scope.launch {
            preferences.deviceConfig.collect { modo = it.backendMode }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = sessionManager.session.value?.accessToken
        val request = if (token == null) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }

        val response = chain.proceed(request)

        // Un 401 de /auth/login es "credenciales incorrectas" y lo maneja
        // RemoteAuthRepository. Se excluye por ruta y no por estado de
        // sesion: onGuardarPromptIa (PLAN.md Parte 16) reautentica llamando
        // /auth/login con la sesion ya activa, y una contrasena mal tecleada
        // ahi no debe expulsar al usuario.
        val esLogin = request.url.encodedPath.endsWith("/auth/login")
        // Solo un 401 a una request que SI llevaba token, en modo REMOTO, es
        // "sesion expirada" y justifica cerrar sesion. Un 401 a una request
        // sin token lo maneja el repositorio que hizo la llamada.
        if (response.code == 401 && !esLogin && token != null && modo == BackendMode.REMOTO) {
            sessionManager.logout()
        }
        return response
    }
}
