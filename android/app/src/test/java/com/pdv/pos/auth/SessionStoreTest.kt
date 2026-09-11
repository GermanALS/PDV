package com.pdv.pos.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pdv.pos.config.FakeTokenCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SessionStoreTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private fun dataStore(tempDir: File): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(dispatcher + SupervisorJob()),
        produceFile = { File(tempDir, "session.preferences_pb") },
    )

    private fun store(tempDir: File) = DataStoreSessionStore(dataStore(tempDir), FakeTokenCipher())

    @Test
    fun `cargar returns null when nothing was persisted`(@TempDir tempDir: File) = runTest(dispatcher) {
        assertNull(store(tempDir).cargar())
    }

    @Test
    fun `round-trips a remote session and never persists the token in plaintext`(@TempDir tempDir: File) =
        runTest(dispatcher) {
            val ds = dataStore(tempDir)
            val store = DataStoreSessionStore(ds, FakeTokenCipher())
            val session = Session("admin", "usuario-1", "rol-1", accessToken = "jwt-secreto")

            store.guardar(session)

            assertEquals(session, store.cargar())
            val persistedToken = ds.data.first()[stringPreferencesKey("session_token_ciphertext")]
            assertNotEquals("jwt-secreto", persistedToken)
        }

    @Test
    fun `round-trips a local session without a token`(@TempDir tempDir: File) = runTest(dispatcher) {
        val store = store(tempDir)
        val session = Session("admin", "usuario-1", "rol-1", accessToken = null)

        store.guardar(session)

        assertEquals(session, store.cargar())
    }

    @Test
    fun `guardar without a token clears a previously stored token`(@TempDir tempDir: File) = runTest(dispatcher) {
        val store = store(tempDir)
        store.guardar(Session("admin", "usuario-1", "rol-1", accessToken = "jwt-viejo"))

        store.guardar(Session("admin", "usuario-1", "rol-1", accessToken = null))

        assertNull(store.cargar()?.accessToken)
    }

    @Test
    fun `limpiar removes the persisted session`(@TempDir tempDir: File) = runTest(dispatcher) {
        val store = store(tempDir)
        store.guardar(Session("admin", "usuario-1", "rol-1", accessToken = "jwt"))

        store.limpiar()

        assertNull(store.cargar())
    }
}
