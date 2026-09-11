package com.pdv.pos.sync

import com.pdv.pos.data.local.SyncConflictDao
import com.pdv.pos.data.local.SyncConflictEntity
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class EventoAditivoSyncEngineTest {

    private val syncConflictDao = mockk<SyncConflictDao>(relaxUnitFun = true)
    private val appLogger = mockk<AppLogger>(relaxed = true)
    private val engine = EventoAditivoSyncEngine(syncConflictDao, appLogger)

    @Test
    fun `applies the delta and records no conflict when the result stays non-negative`() = runTest {
        val valor = engine.combinarYRegistrar(
            entidad = "inventario", entidadLocalId = "inv-1", sucursalId = "suc-1", usuario = "admin",
            base = BigDecimal("5"), deltaLocal = BigDecimal("-2"), deltaRemoto = BigDecimal("-1"),
        )

        assertEquals(BigDecimal("2"), valor)
        coVerify(exactly = 0) { syncConflictDao.insert(any()) }
        coVerify(exactly = 0) { appLogger.log(LogType.SYNC_CONFLICT, any(), any(), any()) }
    }

    @Test
    fun `still applies the delta but records a non-auto-resolved conflict when the result goes negative`() = runTest {
        val conflicto = slot<SyncConflictEntity>()

        val valor = engine.combinarYRegistrar(
            entidad = "inventario", entidadLocalId = "inv-1", sucursalId = "suc-1", usuario = "admin",
            base = BigDecimal("1"), deltaLocal = BigDecimal("-1"), deltaRemoto = BigDecimal("-1"),
        )

        assertEquals(BigDecimal("-1"), valor)
        coVerify(exactly = 1) { syncConflictDao.insert(capture(conflicto)) }
        assertEquals("inventario", conflicto.captured.entidad)
        assertEquals("inv-1", conflicto.captured.entidadLocalId)
        assertEquals("-1", conflicto.captured.valorResuelto)
        assertFalse(conflicto.captured.resueltoAutomaticamente)
        coVerify(exactly = 1) { appLogger.log(LogType.SYNC_CONFLICT, "suc-1", "admin", any()) }
    }
}
