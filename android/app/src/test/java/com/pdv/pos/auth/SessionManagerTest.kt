package com.pdv.pos.auth

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SessionManagerTest {

    // Doble en memoria de SessionStore para los tests de restauracion.
    private class FakeSessionStore(var persisted: Session? = null) : SessionStore {
        override suspend fun cargar(): Session? = persisted
        override suspend fun guardar(session: Session) { persisted = session }
        override suspend fun limpiar() { persisted = null }
    }

    @Test
    fun `iniciarSesion starts a session`() {
        val sessionManager = SessionManager()
        val session = Session(username = "admin", usuarioId = "usuario-1", rolId = "rol-1")

        sessionManager.iniciarSesion(session)

        assertEquals(session, sessionManager.session.value)
    }

    @Test
    fun `logout clears the active session`() {
        val sessionManager = SessionManager()
        sessionManager.iniciarSesion(Session(username = "user1", usuarioId = "usuario-2", rolId = "rol-2"))

        sessionManager.logout()

        assertNull(sessionManager.session.value)
    }

    @Test
    fun `restaurarSesion loads the persisted session`() = runTest {
        val persisted = Session("admin", "usuario-1", "rol-1", accessToken = "jwt")
        val sessionManager = SessionManager(FakeSessionStore(persisted))

        sessionManager.restaurarSesion()

        assertEquals(persisted, sessionManager.session.value)
    }

    @Test
    fun `restaurarSesion does not overwrite a session started this run`() = runTest {
        val sessionManager = SessionManager(FakeSessionStore(Session("viejo", "u-viejo", "r-viejo")))
        val actual = Session("actual", "u-actual", "r-actual", accessToken = "jwt")
        sessionManager.iniciarSesion(actual)

        sessionManager.restaurarSesion()

        assertEquals(actual, sessionManager.session.value)
    }

    @Test
    fun `restaurarSesion leaves the session null when nothing was persisted`() = runTest {
        val sessionManager = SessionManager(FakeSessionStore(persisted = null))

        sessionManager.restaurarSesion()

        assertNull(sessionManager.session.value)
    }
}
