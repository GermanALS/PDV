package com.pdv.pos.sync.push

import com.pdv.pos.data.local.ArticuloEntity
import com.pdv.pos.data.local.InventarioDao
import com.pdv.pos.data.local.MovimientoEntity
import com.pdv.pos.data.remote.InventarioApiService
import com.pdv.pos.data.remote.dto.AjusteDeltaDto
import com.pdv.pos.data.remote.dto.AjusteDeltaRequestDto
import com.pdv.pos.data.remote.dto.InventarioDto
import com.pdv.pos.data.remote.dto.MovimientoDto
import com.pdv.pos.logging.AppLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class InventarioAjustePusherTest {

    private val dao = mockk<InventarioDao>(relaxUnitFun = true)
    private val api = mockk<InventarioApiService>()
    private val appLogger = mockk<AppLogger>(relaxed = true)
    private val pusher = InventarioAjustePusher(dao, api, appLogger, registroRechazos())

    private fun ajuste(localId: String, delta: String) = MovimientoEntity(
        localId = localId,
        remoteId = null,
        sucursalId = "suc-1",
        articuloId = "art-local",
        usuarioId = "admin",
        tipo = "ajuste",
        cantidad = BigDecimal(delta),
        ubicacion = null,
        referenciaTipo = "ajuste_manual",
        referenciaId = null,
        fecha = 1_700_000_000_000L,
        updatedAt = 0L,
        isSynced = false,
        deletedAt = null,
    )

    private fun articulo(remoteId: String?) = ArticuloEntity(
        localId = "art-local", remoteId = remoteId, codigoBarras = null, sku = "SKU-1", nombre = "Cafe",
        descripcion = null, categoria = null, unidadMedida = "unidad", precioVenta = BigDecimal("30"),
        costo = null, activo = true, updatedAt = 0L, isSynced = remoteId != null, deletedAt = null,
    )

    private fun deltaDto(movimientoId: String) = AjusteDeltaDto(
        movimiento = MovimientoDto(
            id = movimientoId, sucursalId = "suc-1", articuloId = "art-remoto", usuarioId = "admin",
            tipo = "ajuste", cantidad = "-2", fecha = "2023-11-14T22:13:20Z", updatedAt = "2023-11-14T22:13:20Z",
        ),
        inventario = InventarioDto(
            id = "inv-remoto", sucursalId = "suc-1", articuloId = "art-remoto", cantidad = "6",
            updatedAt = "2023-11-14T22:13:20Z",
        ),
    )

    @Test
    fun `pushes each pending ajuste as its own delta keyed by the movimiento local id`() = runTest {
        coEvery { dao.getMovimientosAjustePendientes() } returns listOf(ajuste("mov-1", "-2"), ajuste("mov-2", "5"))
        coEvery { dao.getArticulo("art-local") } returns articulo(remoteId = "art-remoto")
        val requests = mutableListOf<AjusteDeltaRequestDto>()
        coEvery { api.ajustarPorDelta(capture(requests)) } answers { deltaDto("mov-remoto-${requests.size}") }

        val resultado = pusher.empujarPendientes()

        assertEquals(PushResultado(subidas = 2, saltadas = 0), resultado)
        assertEquals(listOf("mov-1", "mov-2"), requests.map { it.localId })
        assertEquals(listOf("-2", "5"), requests.map { it.delta })
        assertEquals("art-remoto", requests[0].articuloId)
        assertEquals("2023-11-14T22:13:20Z", requests[0].fecha)
        coVerify { dao.marcarMovimientoSincronizado("mov-1", "mov-remoto-1") }
        coVerify { dao.marcarMovimientoSincronizado("mov-2", "mov-remoto-2") }
    }

    @Test
    fun `defers the ajuste when the articulo has no remote id yet, without discarding it`() = runTest {
        coEvery { dao.getMovimientosAjustePendientes() } returns listOf(ajuste("mov-1", "-2"))
        coEvery { dao.getArticulo("art-local") } returns articulo(remoteId = null)

        val resultado = pusher.empujarPendientes()

        assertEquals(PushResultado(subidas = 0, saltadas = 1), resultado)
        coVerify(exactly = 0) { api.ajustarPorDelta(any()) }
        // Temporal, no irrecuperable: se retoma cuando la entrada del
        // articulo sincronice y obtenga remoteId. No se descarta.
        coVerify(exactly = 0) { dao.marcarMovimientoRechazado(any(), any()) }
    }

    @Test
    fun `marks the ajuste rejected when the backend rejects it with a non-recoverable error`() = runTest {
        coEvery { dao.getMovimientosAjustePendientes() } returns listOf(ajuste("mov-1", "-2"))
        coEvery { dao.getArticulo("art-local") } returns articulo(remoteId = "art-remoto")
        coEvery { api.ajustarPorDelta(any()) } throws httpException(404)

        val resultado = pusher.empujarPendientes()

        assertEquals(PushResultado(subidas = 0, saltadas = 1), resultado)
        // Sin esto, un ajuste irrecuperable se reintentaria en cada ciclo
        // para siempre (PLAN.md Parte 32, hallazgo de verificacion en
        // dispositivo).
        coVerify(exactly = 1) { dao.marcarMovimientoRechazado("mov-1", 404) }
        coVerify(exactly = 0) { dao.marcarMovimientoSincronizado(any(), any()) }
    }

    @Test
    fun `sends the signed delta as a plain string`() = runTest {
        coEvery { dao.getMovimientosAjustePendientes() } returns listOf(ajuste("mov-1", "-2.500"))
        coEvery { dao.getArticulo("art-local") } returns articulo(remoteId = "art-remoto")
        val request = slot<AjusteDeltaRequestDto>()
        coEvery { api.ajustarPorDelta(capture(request)) } returns deltaDto("mov-remoto")

        pusher.empujarPendientes()

        assertEquals("-2.500", request.captured.delta)
    }
}
