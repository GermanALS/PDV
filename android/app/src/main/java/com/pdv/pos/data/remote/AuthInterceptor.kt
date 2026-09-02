package com.pdv.pos.data.remote

import com.pdv.pos.auth.SessionManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

// Adjunta el JWT de la sesion remota a cada request (api-contract.md sec.
// 12) y reacciona a un 401 del backend cerrando la sesion: MainActivity
// observa SessionManager.session y vuelve a LoginScreen cuando pasa a null.
// En modo LOCAL / LOCAL_CON_SINCRONIZACION la sesion no tiene accessToken,
// asi que no se agrega header y el interceptor es transparente.
@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager,
) : Interceptor {

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
        if (response.code == 401 && !esLogin) {
            sessionManager.logout()
        }
        return response
    }
}
