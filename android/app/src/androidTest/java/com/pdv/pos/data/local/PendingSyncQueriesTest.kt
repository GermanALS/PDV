package com.pdv.pos.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/**
 * Checks the `isSynced = 0` pending queries used by the sync pushers on a
 * real SQLite: only unsynced, non-rejected, non-deleted rows are pending.
 */
@RunWith(AndroidJUnit4::class)
class PendingSyncQueriesTest {

    private lateinit var db: PdvDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            PdvDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun ventasPendientes_excludesSyncedRejectedAndDeleted() = runBlocking {
        val dao = db.ventaDao()
        dao.insertVenta(venta("pendiente"))
        dao.insertVenta(venta("sincronizada", isSynced = true))
        dao.insertVenta(venta("rechazada", syncRechazo = 403))
        dao.insertVenta(venta("borrada", deletedAt = 1L))

        assertEquals(listOf("pendiente"), dao.getVentasPendientes().map { it.localId })
        assertEquals(1, dao.contarVentasPendientes())
        assertEquals(1, dao.contarVentasRechazadas())
    }

    @Test
    fun marcarVentaSincronizada_removesItFromPending() = runBlocking {
        val dao = db.ventaDao()
        dao.insertVenta(venta("v-1"))

        dao.marcarVentaSincronizada("v-1", "remote-1")

        assertEquals(0, dao.contarVentasPendientes())
    }

    private fun venta(
        localId: String,
        isSynced: Boolean = false,
        syncRechazo: Int? = null,
        deletedAt: Long? = null,
    ) = VentaEntity(
        localId = localId,
        remoteId = null,
        sucursalId = "suc-1",
        usuarioId = "usr-1",
        folio = "F-$localId",
        fecha = 0L,
        subtotal = BigDecimal.TEN,
        descuento = BigDecimal.ZERO,
        impuestos = BigDecimal.ZERO,
        total = BigDecimal.TEN,
        metodoPago = "efectivo",
        estado = "completada",
        updatedAt = 0L,
        isSynced = isSynced,
        deletedAt = deletedAt,
        syncRechazo = syncRechazo,
    )
}
