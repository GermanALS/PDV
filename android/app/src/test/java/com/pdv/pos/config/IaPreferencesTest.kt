package com.pdv.pos.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
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
class IaPreferencesTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private fun dataStore(tempDir: File): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(dispatcher + SupervisorJob()),
        produceFile = { File(tempDir, "test.preferences_pb") },
    )

    @Test
    fun `setToken never persists the plaintext token`(@TempDir tempDir: File) = runTest(dispatcher) {
        val dataStore = dataStore(tempDir)
        val preferences = IaPreferences(dataStore, FakeTokenCipher())

        preferences.setToken("token-secreto")

        val persisted = dataStore.data.first()[stringPreferencesKey("ia_token_ciphertext")]
        assertNotEquals("token-secreto", persisted)
    }

    @Test
    fun `getToken decrypts back to the original value`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = IaPreferences(dataStore(tempDir), FakeTokenCipher())

        preferences.setToken("token-secreto")

        assertEquals("token-secreto", preferences.getToken())
    }

    @Test
    fun `getToken returns null when no token has been saved`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = IaPreferences(dataStore(tempDir), FakeTokenCipher())

        assertNull(preferences.getToken())
    }
}
