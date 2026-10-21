package com.pdv.pos.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class LimpiezaDataStoreTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private fun dataStore(tempDir: File): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(dispatcher + SupervisorJob()),
        produceFile = { File(tempDir, "test.preferences_pb") },
    )

    @Test
    fun `removes the plaintext ia_token and other obsolete keys but keeps the current ones`(@TempDir tempDir: File) =
        runTest(dispatcher) {
            val dataStore = dataStore(tempDir)
            dataStore.edit {
                it[stringPreferencesKey("ia_token")] = "api-key-en-claro"
                it[stringPreferencesKey("sync_pull_cursor_inventario")] = "2026-09-01T00:00:00Z"
                it[stringPreferencesKey("ia_token_ciphertext")] = "cifrado"
                it[stringPreferencesKey("sync_pull_cursor_inventario_suc-1")] = "2026-10-01T00:00:00Z"
            }

            LimpiezaDataStore(dataStore).limpiarClavesObsoletas()

            val prefs = dataStore.data.first()
            assertNull(prefs[stringPreferencesKey("ia_token")])
            assertNull(prefs[stringPreferencesKey("sync_pull_cursor_inventario")])
            assertEquals("cifrado", prefs[stringPreferencesKey("ia_token_ciphertext")])
            assertEquals("2026-10-01T00:00:00Z", prefs[stringPreferencesKey("sync_pull_cursor_inventario_suc-1")])
        }

    @Test
    fun `a datastore without obsolete keys is left untouched`(@TempDir tempDir: File) = runTest(dispatcher) {
        val dataStore = dataStore(tempDir)
        dataStore.edit { it[stringPreferencesKey("backend_mode")] = "LOCAL" }

        LimpiezaDataStore(dataStore).limpiarClavesObsoletas()

        assertEquals(mapOf(Pair(stringPreferencesKey("backend_mode"), "LOCAL")), dataStore.data.first().asMap())
    }
}
