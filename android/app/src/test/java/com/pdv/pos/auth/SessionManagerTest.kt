package com.pdv.pos.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionManagerTest {

    @Test
    fun `login with valid credentials starts a session`() {
        val sessionManager = SessionManager()

        val success = sessionManager.login("admin", "password")

        assertTrue(success)
        assertEquals(Session("admin"), sessionManager.session.value)
    }

    @Test
    fun `login with invalid credentials does not start a session`() {
        val sessionManager = SessionManager()

        val success = sessionManager.login("admin", "wrong-password")

        assertFalse(success)
        assertNull(sessionManager.session.value)
    }

    @Test
    fun `logout clears the active session`() {
        val sessionManager = SessionManager()
        sessionManager.login("user1", "password")

        sessionManager.logout()

        assertNull(sessionManager.session.value)
    }
}
