package com.pdv.pos.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SessionManagerTest {

    // Doble en memoria de SessionStore para los tests de restauracion.
    private class FakeSessionStore(
        var persisted: Session? = null,
        var ultimoUso: Long? = null,
    ) : SessionStore {
        val usoGuardado = CompletableDeferred<Long>()
        override suspend fun cargar(): Session? = persisted
        override suspend fun guardar(session: Session) { persisted = session }
        override suspend fun limpiar() {
            persisted = null
            ultimoUso = null
        }
        override suspend fun ultimoUso(): Long? = ultimoUso
        override suspend fun guardarUltimoUso(millis: Long) {
            ultimoUso = millis
            usoGuardado.complete(millis)
        }
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

        sessionManager.restaurarSesion(AHORA)

        assertEquals(persisted, sessionManager.session.value)
    }

    @Test
    fun `restaurarSesion does not overwrite a session started this run`() = runTest {
        val sessionManager = SessionManager(FakeSessionStore(Session("viejo", "u-viejo", "r-viejo")))
        val actual = Session("actual", "u-actual", "r-actual", accessToken = "jwt")
        sessionManager.iniciarSesion(actual)

        sessionManager.restaurarSesion(AHORA)

        assertEquals(actual, sessionManager.session.value)
    }

    @Test
    fun `restaurarSesion leaves the session null when nothing was persisted`() = runTest {
        val sessionManager = SessionManager(FakeSessionStore(persisted = null))

        sessionManager.restaurarSesion(AHORA)

        assertNull(sessionManager.session.value)
    }

    @Test
    fun `restaurarSesion discards a session unused for more than 12 hours`() = runTest {
        val store = FakeSessionStore(Session("admin", "usuario-1", "rol-1"), ultimoUso = AHORA - DOCE_HORAS - 1)
        val sessionManager = SessionManager(store)

        sessionManager.restaurarSesion(AHORA)

        assertNull(sessionManager.session.value)
        assertNull(store.persisted)
    }

    @Test
    fun `restaurarSesion keeps a session unused for exactly 12 hours`() = runTest {
        val persisted = Session("admin", "usuario-1", "rol-1")
        val sessionManager = SessionManager(FakeSessionStore(persisted, ultimoUso = AHORA - DOCE_HORAS))

        sessionManager.restaurarSesion(AHORA)

        assertEquals(persisted, sessionManager.session.value)
    }

    @Test
    fun `cerrarSiExpirada logs out after more than 12 hours in background`() = runTest {
        val store = FakeSessionStore(Session("admin", "usuario-1", "rol-1"))
        val sessionManager = SessionManager(store)
        sessionManager.restaurarSesion(AHORA)
        store.ultimoUso = AHORA

        sessionManager.cerrarSiExpirada(AHORA + DOCE_HORAS + 1)

        assertNull(sessionManager.session.value)
    }

    @Test
    fun `cerrarSiExpirada keeps the session within 12 hours`() = runTest {
        val store = FakeSessionStore(Session("admin", "usuario-1", "rol-1"))
        val sessionManager = SessionManager(store)
        sessionManager.restaurarSesion(AHORA)
        store.ultimoUso = AHORA

        sessionManager.cerrarSiExpirada(AHORA + DOCE_HORAS)

        assertEquals("admin", sessionManager.session.value?.username)
    }

    @Test
    fun `a session that never went to background does not expire`() = runTest {
        val sessionManager = SessionManager(FakeSessionStore())
        sessionManager.iniciarSesion(Session("admin", "usuario-1", "rol-1"))

        sessionManager.cerrarSiExpirada(AHORA + 10 * DOCE_HORAS)

        assertEquals("admin", sessionManager.session.value?.username)
    }

    @Test
    fun `registrarUso persists the time the app went to background`() = runBlocking {
        val store = FakeSessionStore()
        val sessionManager = SessionManager(store)
        sessionManager.iniciarSesion(Session("admin", "usuario-1", "rol-1"))

        sessionManager.registrarUso(AHORA)

        assertEquals(AHORA, withTimeout(5_000) { store.usoGuardado.await() })
    }

    private companion object {
        const val AHORA = 1_800_000_000_000L
        const val DOCE_HORAS = 12 * 60 * 60 * 1000L
    }
}
