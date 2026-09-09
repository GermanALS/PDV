package com.pdv.pos.data.remote

import com.pdv.pos.auth.Session
import com.pdv.pos.auth.SessionManager
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AuthInterceptorTest {

    private val baseRequest = Request.Builder().url("http://localhost:8000/api/v1/usuarios").build()
    private val loginRequest = Request.Builder().url("http://localhost:8000/api/v1/auth/login").build()

    private fun chain(
        enviado: CapturingSlot<Request>,
        code: Int,
        request: Request = baseRequest,
    ): Interceptor.Chain {
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(capture(enviado)) } answers {
            Response.Builder()
                .request(enviado.captured)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 401) "Unauthorized" else "OK")
                .body("".toResponseBody())
                .build()
        }
        return chain
    }

    private fun sessionManagerCon(session: Session?): SessionManager =
        SessionManager().apply { session?.let { iniciarSesion(it) } }

    @Test
    fun `adds the bearer header when the session has an access token`() {
        val sessionManager = sessionManagerCon(
            Session("admin", "u1", "r1", accessToken = "jwt-123"),
        )
        val enviado = slot<Request>()

        AuthInterceptor(sessionManager).intercept(chain(enviado, code = 200))

        assertEquals("Bearer jwt-123", enviado.captured.header("Authorization"))
    }

    @Test
    fun `does not add a header when the session has no token (local mode)`() {
        val sessionManager = sessionManagerCon(Session("admin", "u1", "r1", accessToken = null))
        val enviado = slot<Request>()

        AuthInterceptor(sessionManager).intercept(chain(enviado, code = 200))

        assertNull(enviado.captured.header("Authorization"))
    }

    @Test
    fun `does not add a header when there is no session`() {
        val sessionManager = sessionManagerCon(null)
        val enviado = slot<Request>()

        AuthInterceptor(sessionManager).intercept(chain(enviado, code = 200))

        assertNull(enviado.captured.header("Authorization"))
    }

    @Test
    fun `clears the session on a 401 response`() {
        val sessionManager = sessionManagerCon(
            Session("admin", "u1", "r1", accessToken = "jwt-123"),
        )
        val enviado = slot<Request>()

        AuthInterceptor(sessionManager).intercept(chain(enviado, code = 401))

        assertNull(sessionManager.session.value)
    }

    @Test
    fun `a 401 to a tokenless request does not clear a local session`() {
        // Modo LOCAL / LOCAL_CON_SINCRONIZACION: sesion viva pero sin JWT
        // (login local). El backend con enforcement (PLAN.md Parte 21)
        // responde 401 a la request sin header; no debe expulsar al usuario.
        val sessionManager = sessionManagerCon(Session("admin", "u1", "r1", accessToken = null))
        val enviado = slot<Request>()

        AuthInterceptor(sessionManager).intercept(chain(enviado, code = 401))

        assertNotNull(sessionManager.session.value)
    }

    @Test
    fun `keeps the session on a successful response`() {
        val sessionManager = sessionManagerCon(
            Session("admin", "u1", "r1", accessToken = "jwt-123"),
        )
        val enviado = slot<Request>()

        AuthInterceptor(sessionManager).intercept(chain(enviado, code = 200))

        assertNotNull(sessionManager.session.value)
    }

    @Test
    fun `a 401 without an active session is a no-op`() {
        val sessionManager = sessionManagerCon(null)
        val enviado = slot<Request>()

        AuthInterceptor(sessionManager).intercept(chain(enviado, code = 401))

        assertNull(sessionManager.session.value)
    }

    @Test
    fun `a 401 from auth login does not clear an active session`() {
        // Reautenticacion de onGuardarPromptIa (Parte 16): contrasena mal
        // tecleada -> 401 de /auth/login, pero la sesion sigue viva.
        val sessionManager = sessionManagerCon(
            Session("admin", "u1", "r1", accessToken = "jwt-123"),
        )
        val enviado = slot<Request>()

        AuthInterceptor(sessionManager).intercept(chain(enviado, code = 401, request = loginRequest))

        assertNotNull(sessionManager.session.value)
    }
}
