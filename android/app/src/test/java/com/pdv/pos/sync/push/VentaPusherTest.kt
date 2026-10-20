package com.pdv.pos.sync.push

import com.pdv.pos.data.local.ArticuloEntity
import com.pdv.pos.data.local.SyncConflictDao
import com.pdv.pos.data.local.SyncConflictEntity
import com.pdv.pos.data.local.VentaDao
import com.pdv.pos.data.local.VentaDetalleEntity
import com.pdv.pos.data.local.VentaEntity
import com.pdv.pos.data.remote.VentaApiService
import com.pdv.pos.data.remote.dto.VentaDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import java.io.IOException
import java.math.BigDecimal
import kotlin.test.assertFailsWith

class VentaPusherTest {

    private val dao = mockk<VentaDao>(relaxUnitFun = true)
    private val api = mockk<VentaApiService>()
    private val conflictDao = mockk<SyncConflictDao>(relaxUnitFun = true)
    private val pusher = VentaPusher(dao, api, registroRechazos(conflictDao))

    private fun venta(localId: String) = VentaEntity(
        localId = localId,
        remoteId = null,
        sucursalId = "suc-1",
        usuarioId = "u-1",
        folio = "F-$localId",
        fecha = 1_700_000_000_000L,
        subtotal = BigDecimal("10"),
        descuento = BigDecimal.ZERO,
        impuestos = BigDecimal.ZERO,
        total = BigDecimal("10"),
        metodoPago = "efectivo",
        estado = "completada",
        updatedAt = 0L,
        isSynced = false,
        deletedAt = null,
    )

    private fun ventaDto(id: String, localId: String) = VentaDto(
        id = id,
        localId = localId,
        sucursalId = "suc-1",
        usuarioId = "u-1",
        folio = "F-$localId",
        fecha = "2023-11-14T22:13:20Z",
        subtotal = "10",
        descuento = "0",
        impuestos = "0",
        total = "10",
        metodoPago = "efectivo",
        estado = "completada",
        updatedAt = "2023-11-14T22:13:20Z",
        lineas = emptyList(),
    )

    @Test
    fun `pushes a pending venta and marks it plus its children synced with the remote id`() = runTest {
        coEvery { dao.getVentasPendientes() } returns listOf(venta("v-1"))
        coEvery { dao.getDetallesDeVenta("v-1") } returns emptyList()
        coEvery { api.createVenta(any()) } returns ventaDto(id = "remoto-1", localId = "v-1")

        val resultado = pusher.empujarPendientes()

        assertEquals(PushResultado(subidas = 1, saltadas = 0), resultado)
        coVerify(exactly = 1) { dao.marcarVentaSincronizada("v-1", "remoto-1") }
        coVerify(exactly = 1) { dao.marcarDetallesVentaSincronizados("v-1") }
        coVerify(exactly = 1) { dao.marcarMovimientosVentaSincronizados("v-1") }
    }

    @Test
    fun `a 403 on one venta marks it rejected with the code, records a conflict and keeps going`() = runTest {
        coEvery { dao.getVentasPendientes() } returns listOf(venta("v-1"), venta("v-2"))
        coEvery { dao.getDetallesDeVenta(any()) } returns emptyList()
        coEvery { api.createVenta(match { it.localId == "v-1" }) } throws httpException(403)
        coEvery { api.createVenta(match { it.localId == "v-2" }) } returns ventaDto("remoto-2", "v-2")

        val resultado = pusher.empujarPendientes()

        assertEquals(PushResultado(subidas = 1, saltadas = 1), resultado)
        coVerify(exactly = 0) { dao.marcarVentaSincronizada("v-1", any()) }
        // Rechazada con su codigo (no isSynced), no solo "saltada" en el
        // contador: de lo contrario el proximo ciclo la reintenta desde cero
        // (PLAN.md Parte 32) y nadie se entera de que no llego (Parte 36, A-6).
        coVerify(exactly = 1) { dao.marcarVentaRechazada("v-1", 403) }
        coVerify(exactly = 1) { dao.marcarMovimientosVentaSincronizados("v-1") }
        val conflicto = slot<SyncConflictEntity>()
        coVerify(exactly = 1) { conflictDao.insert(capture(conflicto)) }
        assertEquals("venta", conflicto.captured.entidad)
        assertEquals("v-1", conflicto.captured.entidadLocalId)
        assertEquals("HTTP 403", conflicto.captured.valorRemoto)
        assertEquals(POLITICA_RECHAZO_BACKEND, conflicto.captured.politicaAplicada)
        assertEquals(false, conflicto.captured.resueltoAutomaticamente)
        coVerify(exactly = 1) { dao.marcarVentaSincronizada("v-2", "remoto-2") }
    }

    private fun detalle(articuloId: String) = VentaDetalleEntity(
        localId = "det-1",
        remoteId = null,
        ventaId = "v-1",
        articuloId = articuloId,
        cantidad = BigDecimal("1"),
        precioUnitario = BigDecimal("10"),
        subtotal = BigDecimal("10"),
        updatedAt = 0L,
        isSynced = false,
        deletedAt = null,
    )

    private fun articulo(localId: String, remoteId: String?) = ArticuloEntity(
        localId = localId, remoteId = remoteId, codigoBarras = null, sku = "SKU", nombre = "Articulo",
        descripcion = null, categoria = null, unidadMedida = "unidad", precioVenta = BigDecimal("10"),
        costo = null, activo = true, updatedAt = 0L, isSynced = remoteId != null, deletedAt = null,
    )

    @Test
    fun `resolves each linea's articulo_id to its remote id before pushing`() = runTest {
        coEvery { dao.getVentasPendientes() } returns listOf(venta("v-1"))
        coEvery { dao.getDetallesDeVenta("v-1") } returns listOf(detalle("art-local"))
        coEvery { dao.getArticulo("art-local") } returns articulo("art-local", remoteId = "art-remoto")
        val request = slot<com.pdv.pos.data.remote.dto.VentaCreateRequestDto>()
        coEvery { api.createVenta(capture(request)) } returns ventaDto("remoto-1", "v-1")

        val resultado = pusher.empujarPendientes()

        assertEquals(PushResultado(subidas = 1, saltadas = 0), resultado)
        assertEquals("art-remoto", request.captured.lineas.first().articuloId)
    }

    @Test
    fun `defers the venta without pushing when a linea's articulo has no remote id yet`() = runTest {
        coEvery { dao.getVentasPendientes() } returns listOf(venta("v-1"))
        coEvery { dao.getDetallesDeVenta("v-1") } returns listOf(detalle("art-local"))
        coEvery { dao.getArticulo("art-local") } returns articulo("art-local", remoteId = null)

        val resultado = pusher.empujarPendientes()

        assertEquals(PushResultado(subidas = 0, saltadas = 1), resultado)
        coVerify(exactly = 0) { api.createVenta(any()) }
        // Temporal, no irrecuperable: no se descarta, se reintenta cuando el
        // articulo tenga remoteId.
        coVerify(exactly = 0) { dao.marcarVentaRechazada(any(), any()) }
    }

    @Test
    fun `an IOException stops the cycle so WorkManager can retry`() = runTest {
        coEvery { dao.getVentasPendientes() } returns listOf(venta("v-1"))
        coEvery { dao.getDetallesDeVenta(any()) } returns emptyList()
        coEvery { api.createVenta(any()) } throws IOException("sin red")

        assertFailsWith<IOException> { pusher.empujarPendientes() }
    }

    @Test
    fun `a 401 stops the cycle`() = runTest {
        coEvery { dao.getVentasPendientes() } returns listOf(venta("v-1"))
        coEvery { dao.getDetallesDeVenta(any()) } returns emptyList()
        coEvery { api.createVenta(any()) } throws httpException(401)

        assertFailsWith<HttpException> { pusher.empujarPendientes() }
        coVerify(exactly = 0) { dao.marcarVentaRechazada(any(), any()) }
        coVerify(exactly = 0) { conflictDao.insert(any()) }
    }
}
