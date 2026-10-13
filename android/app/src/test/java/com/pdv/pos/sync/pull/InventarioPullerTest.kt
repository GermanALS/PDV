package com.pdv.pos.sync.pull

import com.pdv.pos.data.local.ArticuloEntity
import com.pdv.pos.data.local.InventarioDao
import com.pdv.pos.data.local.InventarioEntity
import com.pdv.pos.data.local.MovimientoEntity
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

    private fun movimiento(tipo: String, cantidad: String) = MovimientoEntity(
        localId = "mov-$tipo-$cantidad", remoteId = null, sucursalId = "suc-1", articuloId = "art-local",
        usuarioId = "admin", tipo = tipo, cantidad = BigDecimal(cantidad), ubicacion = null,
        referenciaTipo = null, referenciaId = null, fecha = 0L, updatedAt = 0L, isSynced = false, deletedAt = null,
    )

    private fun stubReconciliacionPendiente(vararg items: InventarioItemDto) {
        stubPage(*items)
        coEvery { syncStateStore.inventarioReconciliado("suc-1") } returns false
    }

    private fun stubPage(vararg items: InventarioItemDto) {
        coEvery { syncStateStore.inventarioReconciliado("suc-1") } returns true
        coEvery { syncStateStore.pullDesde("inventario", "suc-1") } returns null
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
        coVerify { syncStateStore.setPullCursor("inventario", "suc-1", "2026-08-20T14:00:00Z") }
    }

    @Test
    fun `overwrites a local row whose movimientos were already synced even if the row is flagged dirty`() = runTest {
        stubPage(item(cantidad = "9"))
        coEvery { dao.getArticuloByRemoteId("art-remoto") } returns articulo()
        coEvery { dao.getInventario("suc-1", "art-local") } returns inventarioLocal("20", isSynced = false)
        coEvery { dao.getMovimientosPendientes("suc-1", "art-local") } returns emptyList()
        val actualizada = slot<InventarioEntity>()
        coEvery { dao.updateInventario(capture(actualizada)) } returns Unit

        puller.pull("suc-1")

        assertEquals(BigDecimal("9"), actualizada.captured.cantidad)
        assertTrue(actualizada.captured.isSynced)
        assertEquals("B2", actualizada.captured.ubicacion)
    }

    @Test
    fun `keeps the local row and records the divergence when movimientos are pending and the remote value differs`() = runTest {
        stubPage(item(cantidad = "9"))
        coEvery { dao.getArticuloByRemoteId("art-remoto") } returns articulo()
        coEvery { dao.getInventario("suc-1", "art-local") } returns inventarioLocal("15", isSynced = false)
        coEvery { dao.getMovimientosPendientes("suc-1", "art-local") } returns listOf(movimiento("salida", "1"))

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
    fun `asks the backend for rows since the cursor minus the overlap window`() = runTest {
        stubPage(item(cantidad = "9"))
        coEvery { syncStateStore.pullDesde("inventario", "suc-1") } returns "2026-08-20T13:58:00Z"
        coEvery { dao.getArticuloByRemoteId("art-remoto") } returns null

        puller.pull("suc-1")

        coVerify { api.getInventario("suc-1", null, 1, 100, "2026-08-20T13:58:00Z") }
    }

    @Test
    fun `reconciliation pulls the whole inventory ignoring the cursor and marks the sucursal as reconciled`() = runTest {
        stubReconciliacionPendiente(item(cantidad = "9"))
        coEvery { syncStateStore.pullDesde("inventario", "suc-1") } returns "2026-08-20T13:58:00Z"
        coEvery { dao.getArticuloByRemoteId("art-remoto") } returns articulo()
        coEvery { dao.getInventario("suc-1", "art-local") } returns inventarioLocal("9", isSynced = true)
        coEvery { dao.getMovimientosPendientes("suc-1", "art-local") } returns emptyList()

        puller.pull("suc-1")

        coVerify { api.getInventario("suc-1", null, 1, 100, null) }
        coVerify { syncStateStore.marcarInventarioReconciliado("suc-1") }
        coVerify(exactly = 0) { eventoAditivoSyncEngine.registrarReconciliacionInventario(any(), any(), any()) }
    }

    @Test
    fun `reconciliation overwrites a diverged row without pendings and records a single summary`() = runTest {
        stubReconciliacionPendiente(item(cantidad = "20"))
        coEvery { dao.getArticuloByRemoteId("art-remoto") } returns articulo()
        coEvery { dao.getInventario("suc-1", "art-local") } returns inventarioLocal("12", isSynced = false)
        coEvery { dao.getMovimientosPendientes("suc-1", "art-local") } returns emptyList()
        val actualizada = slot<InventarioEntity>()
        coEvery { dao.updateInventario(capture(actualizada)) } returns Unit

        puller.pull("suc-1")

        assertEquals(BigDecimal("20"), actualizada.captured.cantidad)
        coVerify(exactly = 1) { eventoAditivoSyncEngine.registrarReconciliacionInventario("suc-1", "system", 1) }
        coVerify { syncStateStore.marcarInventarioReconciliado("suc-1") }
    }

    @Test
    fun `reconciliation sets a row with pendings to remote plus the effect of its pendings`() = runTest {
        stubReconciliacionPendiente(item(cantidad = "9"))
        coEvery { dao.getArticuloByRemoteId("art-remoto") } returns articulo()
        coEvery { dao.getInventario("suc-1", "art-local") } returns inventarioLocal("15", isSynced = false)
        coEvery { dao.getMovimientosPendientes("suc-1", "art-local") } returns
            listOf(movimiento("salida", "2"), movimiento("entrada", "4"), movimiento("ajuste", "-1"))
        val actualizada = slot<InventarioEntity>()
        coEvery { dao.updateInventario(capture(actualizada)) } returns Unit

        puller.pull("suc-1")

        assertEquals(BigDecimal("10"), actualizada.captured.cantidad)
        coVerify(exactly = 0) { eventoAditivoSyncEngine.registrarDivergenciaLocalGana(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { eventoAditivoSyncEngine.registrarReconciliacionInventario("suc-1", "system", 1) }
    }

    @Test
    fun `does not mark the sucursal as reconciled when the pull fails`() = runTest {
        coEvery { syncStateStore.inventarioReconciliado("suc-1") } returns false
        coEvery { api.getInventario(any(), any(), any(), any(), any()) } throws java.io.IOException("sin conexion")

        runCatching { puller.pull("suc-1") }

        coVerify(exactly = 0) { syncStateStore.marcarInventarioReconciliado(any()) }
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
