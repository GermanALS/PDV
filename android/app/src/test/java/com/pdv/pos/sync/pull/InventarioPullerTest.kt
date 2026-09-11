package com.pdv.pos.sync.pull

import com.pdv.pos.data.local.ArticuloEntity
import com.pdv.pos.data.local.InventarioDao
import com.pdv.pos.data.local.InventarioEntity
import com.pdv.pos.data.remote.InventarioApiService
import com.pdv.pos.data.remote.dto.InventarioItemDto
import com.pdv.pos.data.remote.dto.InventarioListResponseDto
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.sync.EventoAditivoSyncEngine
import com.pdv.pos.sync.SyncStateStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class InventarioPullerTest {

    private val api = mockk<InventarioApiService>()
    private val dao = mockk<InventarioDao>(relaxUnitFun = true)
    private val syncStateStore = mockk<SyncStateStore>(relaxUnitFun = true)
    private val eventoAditivoSyncEngine = mockk<EventoAditivoSyncEngine>(relaxUnitFun = true)
    private val appLogger = mockk<AppLogger>(relaxed = true)
    private val puller = InventarioPuller(api, dao, syncStateStore, eventoAditivoSyncEngine, appLogger)

    private fun item(cantidad: String, updatedAt: String = "2026-08-20T14:00:00Z") = InventarioItemDto(
        articuloId = "art-remoto",
        sku = "SKU-1",
        nombre = "Cafe",
        unidadMedida = "unidad",
        precioVenta = "30",
        cantidad = cantidad,
        ubicacion = "B2",
        updatedAt = updatedAt,
    )

    private fun articulo() = ArticuloEntity(
        localId = "art-local", remoteId = "art-remoto", codigoBarras = null, sku = "SKU-1", nombre = "Cafe",
        descripcion = null, categoria = null, unidadMedida = "unidad", precioVenta = BigDecimal("30"),
        costo = null, activo = true, updatedAt = 0L, isSynced = true, deletedAt = null,
    )

    private fun inventarioLocal(cantidad: String, isSynced: Boolean) = InventarioEntity(
        localId = "inv-local", remoteId = null, sucursalId = "suc-1", articuloId = "art-local",
        cantidad = BigDecimal(cantidad), cantidadNum = BigDecimal(cantidad).toDouble(), ubicacion = "A1",
        updatedAt = 0L, isSynced = isSynced, deletedAt = null,
    )

    private fun stubPage(vararg items: InventarioItemDto) {
        coEvery { syncStateStore.pullCursor("inventario") } returns null
        coEvery { api.getInventario(any(), any(), any(), any(), any()) } returns
            InventarioListResponseDto(items = items.toList(), page = 1, pageSize = 100, total = items.size)
    }

    @Test
    fun `inserts a synced row when the article is known but has no local inventory`() = runTest {
        stubPage(item(cantidad = "12"))
        coEvery { dao.getArticuloByRemoteId("art-remoto") } returns articulo()
        coEvery { dao.getInventario("suc-1", "art-local") } returns null
        val nueva = slot<InventarioEntity>()
        coEvery { dao.insertInventario(capture(nueva)) } returns Unit

        puller.pull("suc-1")

        assertEquals(BigDecimal("12"), nueva.captured.cantidad)
        assertTrue(nueva.captured.isSynced)
        coVerify { syncStateStore.setPullCursor("inventario", "2026-08-20T14:00:00Z") }
    }

    @Test
    fun `overwrites a clean local row from the remote value`() = runTest {
        stubPage(item(cantidad = "9"))
        coEvery { dao.getArticuloByRemoteId("art-remoto") } returns articulo()
        coEvery { dao.getInventario("suc-1", "art-local") } returns inventarioLocal("20", isSynced = true)
        val actualizada = slot<InventarioEntity>()
        coEvery { dao.updateInventario(capture(actualizada)) } returns Unit

        puller.pull("suc-1")

        assertEquals(BigDecimal("9"), actualizada.captured.cantidad)
        assertTrue(actualizada.captured.isSynced)
        assertEquals("B2", actualizada.captured.ubicacion)
    }

    @Test
    fun `keeps a dirty local row and records the divergence when the remote value differs`() = runTest {
        stubPage(item(cantidad = "9"))
        coEvery { dao.getArticuloByRemoteId("art-remoto") } returns articulo()
        coEvery { dao.getInventario("suc-1", "art-local") } returns inventarioLocal("15", isSynced = false)

        puller.pull("suc-1")

        coVerify(exactly = 0) { dao.updateInventario(any()) }
        coVerify {
            eventoAditivoSyncEngine.registrarDivergenciaLocalGana(
                entidad = "inventario", entidadLocalId = "inv-local", sucursalId = "suc-1", usuario = "system",
                valorLocal = BigDecimal("15"), valorRemoto = BigDecimal("9"),
            )
        }
    }

    @Test
    fun `skips an item whose article is unknown locally`() = runTest {
        stubPage(item(cantidad = "9"))
        coEvery { dao.getArticuloByRemoteId("art-remoto") } returns null

        puller.pull("suc-1")

        coVerify(exactly = 0) { dao.insertInventario(any()) }
        coVerify(exactly = 0) { dao.updateInventario(any()) }
    }
}
