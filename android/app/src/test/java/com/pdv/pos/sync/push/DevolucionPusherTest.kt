package com.pdv.pos.sync.push

import com.pdv.pos.data.local.ArticuloEntity
import com.pdv.pos.data.local.DevolucionDao
import com.pdv.pos.data.local.DevolucionDetalleEntity
import com.pdv.pos.data.local.DevolucionEntity
import com.pdv.pos.data.remote.DevolucionApiService
import com.pdv.pos.data.remote.dto.DevolucionCreateRequestDto
import com.pdv.pos.data.remote.dto.DevolucionDto
import com.pdv.pos.logging.AppLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal

// Complementa SyncPushersHappyPathTest (caso feliz sin lineas/venta) con el
// remapeo de articulo_id/venta_id local -> remoto (PLAN.md Parte 32,
// hallazgo de verificacion en dispositivo: enviar los ids locales producia
// un 404 "articulo/venta no encontrada" permanente).
class DevolucionPusherTest {

    private val dao = mockk<DevolucionDao>(relaxUnitFun = true)
    private val api = mockk<DevolucionApiService>()
    private val appLogger = mockk<AppLogger>(relaxed = true)
    private val pusher = DevolucionPusher(dao, api, appLogger)

    private fun devolucion(ventaId: String?) = DevolucionEntity(
        localId = "dev-1", remoteId = null, sucursalId = "suc-1", usuarioId = "u-1", ventaId = ventaId,
        folio = "D-001", fecha = 1L, estado = "pendiente", updatedAt = 0L, isSynced = false, deletedAt = null,
    )

    private fun detalle(articuloId: String) = DevolucionDetalleEntity(
        localId = "det-1", remoteId = null, devolucionId = "dev-1", articuloId = articuloId,
        cantidad = BigDecimal("1"), motivo = null, condicion = null, updatedAt = 0L, isSynced = false, deletedAt = null,
    )

    private fun articulo(remoteId: String?) = ArticuloEntity(
        localId = "art-local", remoteId = remoteId, codigoBarras = null, sku = "SKU", nombre = "Articulo",
        descripcion = null, categoria = null, unidadMedida = "unidad", precioVenta = BigDecimal("10"),
        costo = null, activo = true, updatedAt = 0L, isSynced = remoteId != null, deletedAt = null,
    )

    private fun devolucionDto() = DevolucionDto(
        id = "dev-remoto", localId = "dev-1", sucursalId = "suc-1", usuarioId = "u-1", ventaId = null,
        folio = "D-001", fecha = "2023-11-14T22:13:20Z", estado = "pendiente",
        updatedAt = "2023-11-14T22:13:20Z", lineas = emptyList(),
    )

    @Test
    fun `resolves the linea articulo_id and the venta_id to their remote ids`() = runTest {
        coEvery { dao.getDevolucionesPendientes() } returns listOf(devolucion(ventaId = "venta-local"))
        coEvery { dao.getDetallesDeDevolucion("dev-1") } returns listOf(detalle("art-local"))
        coEvery { dao.getArticulo("art-local") } returns articulo(remoteId = "art-remoto")
        coEvery { dao.getVentaRemoteId("venta-local") } returns "venta-remoto"
        val request = slot<DevolucionCreateRequestDto>()
        coEvery { api.createDevolucion(capture(request)) } returns devolucionDto()

        val resultado = pusher.empujarPendientes()

        assertEquals(PushResultado(subidas = 1, saltadas = 0), resultado)
        assertEquals("venta-remoto", request.captured.ventaId)
        assertEquals("art-remoto", request.captured.lineas.first().articuloId)
    }

    @Test
    fun `defers without pushing when the referenced venta has not synced yet`() = runTest {
        coEvery { dao.getDevolucionesPendientes() } returns listOf(devolucion(ventaId = "venta-local"))
        coEvery { dao.getDetallesDeDevolucion("dev-1") } returns emptyList()
        coEvery { dao.getVentaRemoteId("venta-local") } returns null

        val resultado = pusher.empujarPendientes()

        assertEquals(PushResultado(subidas = 0, saltadas = 1), resultado)
        coVerify(exactly = 0) { api.createDevolucion(any()) }
        coVerify(exactly = 0) { dao.marcarDevolucionDescartada(any()) }
    }

    @Test
    fun `defers without pushing when a linea's articulo has not synced yet`() = runTest {
        coEvery { dao.getDevolucionesPendientes() } returns listOf(devolucion(ventaId = null))
        coEvery { dao.getDetallesDeDevolucion("dev-1") } returns listOf(detalle("art-local"))
        coEvery { dao.getArticulo("art-local") } returns articulo(remoteId = null)

        val resultado = pusher.empujarPendientes()

        assertEquals(PushResultado(subidas = 0, saltadas = 1), resultado)
        coVerify(exactly = 0) { api.createDevolucion(any()) }
    }

    @Test
    fun `a devolucion without a venta reference sends a null venta_id, not a local one`() = runTest {
        coEvery { dao.getDevolucionesPendientes() } returns listOf(devolucion(ventaId = null))
        coEvery { dao.getDetallesDeDevolucion("dev-1") } returns emptyList()
        val request = slot<DevolucionCreateRequestDto>()
        coEvery { api.createDevolucion(capture(request)) } returns devolucionDto()

        pusher.empujarPendientes()

        assertNull(request.captured.ventaId)
        coVerify(exactly = 0) { dao.getVentaRemoteId(any()) }
    }
}
