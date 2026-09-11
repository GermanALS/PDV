package com.pdv.pos.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SyncStateStoreTest {

    private fun store(tempDir: File): SyncStateStore {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tempDir, "sync.preferences_pb") }
        )
        return SyncStateStore(dataStore)
    }

    @Test
    fun `default state has no last success and no error`(@TempDir tempDir: File) = runTest {
        val state = store(tempDir).state.first()

        assertNull(state.lastSuccessAtMillis)
        assertNull(state.lastError)
    }

    @Test
    fun `registrarExito sets the timestamp and clears a previous error`(@TempDir tempDir: File) = runTest {
        val store = store(tempDir)
        store.registrarError("host inalcanzable")

        store.registrarExito(nowMillis = 1_700_000_000_000L)

        val state = store.state.first()
        assertEquals(1_700_000_000_000L, state.lastSuccessAtMillis)
        assertNull(state.lastError)
    }

    @Test
    fun `registrarError keeps the last success timestamp`(@TempDir tempDir: File) = runTest {
        val store = store(tempDir)
        store.registrarExito(nowMillis = 1_700_000_000_000L)

        store.registrarError("timeout")

        val state = store.state.first()
        assertEquals(1_700_000_000_000L, state.lastSuccessAtMillis)
        assertEquals("timeout", state.lastError)
    }

    @Test
    fun `pull cursor round-trips per entidad`(@TempDir tempDir: File) = runTest {
        val store = store(tempDir)

        store.setPullCursor("inventario", "2026-09-09T12:00:00Z")
        store.setPullCursor("cortes_caja", "2026-09-09T13:30:00Z")

        assertEquals("2026-09-09T12:00:00Z", store.pullCursor("inventario"))
        assertEquals("2026-09-09T13:30:00Z", store.pullCursor("cortes_caja"))
        assertNull(store.pullCursor("retiros_efectivo"))
    }
}
