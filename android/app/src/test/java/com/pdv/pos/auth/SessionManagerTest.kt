package com.pdv.pos.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SessionManagerTest {

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
}
