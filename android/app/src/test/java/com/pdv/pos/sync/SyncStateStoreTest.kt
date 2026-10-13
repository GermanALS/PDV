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

        store.setPullCursor("inventario", "suc-1", "2026-09-09T12:00:00Z")
        store.setPullCursor("cortes_caja", "suc-1", "2026-09-09T13:30:00Z")

        assertEquals("2026-09-09T12:00:00Z", store.pullCursor("inventario", "suc-1"))
        assertEquals("2026-09-09T13:30:00Z", store.pullCursor("cortes_caja", "suc-1"))
        assertNull(store.pullCursor("retiros_efectivo", "suc-1"))
    }

    @Test
    fun `a different sucursal does not inherit the pull cursor`(@TempDir tempDir: File) = runTest {
        val store = store(tempDir)
        store.setPullCursor("inventario", "suc-1", "2026-09-09T12:00:00Z")

        assertNull(store.pullCursor("inventario", "suc-2"))
        assertNull(store.pullDesde("inventario", "suc-2"))
    }

    @Test
    fun `inventory reconciliation flag is per sucursal and defaults to false`(@TempDir tempDir: File) = runTest {
        val store = store(tempDir)

        assertEquals(false, store.inventarioReconciliado("suc-1"))
        store.marcarInventarioReconciliado("suc-1")

        assertEquals(true, store.inventarioReconciliado("suc-1"))
        assertEquals(false, store.inventarioReconciliado("suc-2"))
    }

    @Test
    fun `pullDesde subtracts the overlap window from the cursor`(@TempDir tempDir: File) = runTest {
        val store = store(tempDir)
        store.setPullCursor("inventario", "suc-1", "2026-09-09T12:00:00Z")

        assertEquals("2026-09-09T11:58:00Z", store.pullDesde("inventario", "suc-1"))
    }
}
