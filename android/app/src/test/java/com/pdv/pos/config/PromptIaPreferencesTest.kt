package com.pdv.pos.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.ia.PROMPT_SISTEMA_DEFAULT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PromptIaPreferencesTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private fun dataStore(tempDir: File): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(dispatcher + SupervisorJob()),
        produceFile = { File(tempDir, "test.preferences_pb") },
    )

    @Test
    fun `a fresh install reads back the approved default text`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = PromptIaPreferences(dataStore(tempDir))

        assertEquals(PROMPT_SISTEMA_DEFAULT, preferences.prompt.first())
    }

    @Test
    fun `setPrompt persists the new text and overrides the default`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = PromptIaPreferences(dataStore(tempDir))

        preferences.setPrompt("Prompt personalizado")

        assertEquals("Prompt personalizado", preferences.prompt.first())
    }
}
