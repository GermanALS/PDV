package com.pdv.pos.sync.push

import com.pdv.pos.data.local.ArticuloEntity
import com.pdv.pos.data.local.InventarioDao
import com.pdv.pos.data.local.InventarioEntity
import com.pdv.pos.data.local.MovimientoEntity
import com.pdv.pos.data.remote.InventarioApiService
import com.pdv.pos.data.remote.dto.AjusteInventarioDto
import com.pdv.pos.data.remote.dto.ArticuloDto
import com.pdv.pos.data.remote.dto.ArticuloEdicionRequestDto
import com.pdv.pos.data.remote.dto.InventarioDto
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
    private val pusher = InventarioAjustePusher(dao, api, appLogger)

    private fun ajuste(localId: String, articuloId: String) = MovimientoEntity(
        localId = localId,
        remoteId = null,
        sucursalId = "suc-1",
        articuloId = articuloId,
        usuarioId = "admin",
        tipo = "ajuste",
        cantidad = BigDecimal("-2"),
        ubicacion = null,
        referenciaTipo = "ajuste_manual",
        referenciaId = null,
        fecha = 1L,
        updatedAt = 0L,
        isSynced = false,
        deletedAt = null,
    )

    private fun articulo(remoteId: String?) = ArticuloEntity(
        localId = "art-local", remoteId = remoteId, codigoBarras = null, sku = "SKU-1", nombre = "Cafe",
        descripcion = null, categoria = null, unidadMedida = "unidad", precioVenta = BigDecimal("30"),
        costo = null, activo = true, updatedAt = 0L, isSynced = remoteId != null, deletedAt = null,
    )

    private fun inventario() = InventarioEntity(
        localId = "inv-local", remoteId = null, sucursalId = "suc-1", articuloId = "art-local",
        cantidad = BigDecimal("6"), cantidadNum = 6.0, ubicacion = "A1", updatedAt = 0L, isSynced = false, deletedAt = null,
    )

    private fun ajusteDto(conMovimiento: Boolean) = AjusteInventarioDto(
        articulo = ArticuloDto(id = "art-remoto", sku = "SKU-1", nombre = "Cafe", unidadMedida = "unidad", precioVenta = "30", updatedAt = "2023-11-14T22:13:20Z"),
        inventario = InventarioDto(id = "inv-remoto", sucursalId = "suc-1", articuloId = "art-remoto", cantidad = "6", updatedAt = "2023-11-14T22:13:20Z"),
        movimiento = if (conMovimiento) {
            com.pdv.pos.data.remote.dto.MovimientoDto(
                id = "mov-remoto", sucursalId = "suc-1", articuloId = "art-remoto", usuarioId = "admin",
                tipo = "ajuste", cantidad = "-2", fecha = "2023-11-14T22:13:20Z", updatedAt = "2023-11-14T22:13:20Z",
            )
        } else {
            null
        },
    )

    @Test
    fun `groups pending ajustes per articulo and pushes the current local quantity as the new value`() = runTest {
        coEvery { dao.getMovimientosAjustePendientes() } returns listOf(ajuste("mov-1", "art-local"), ajuste("mov-2", "art-local"))
        coEvery { dao.getArticulo("art-local") } returns articulo(remoteId = "art-remoto")
        coEvery { dao.getInventario("suc-1", "art-local") } returns inventario()
        val request = slot<ArticuloEdicionRequestDto>()
        coEvery { api.ajustarArticulo("art-remoto", capture(request)) } returns ajusteDto(conMovimiento = true)

        val resultado = pusher.empujarPendientes()

        assertEquals(PushResultado(subidas = 2, saltadas = 0), resultado)
        assertEquals("6", request.captured.cantidad)
        coVerify { dao.marcarMovimientoSincronizado("mov-1", "mov-remoto") }
        coVerify { dao.marcarMovimientoSincronizado("mov-2", "mov-remoto") }
        coVerify { dao.marcarArticuloSincronizado("art-local", "art-remoto") }
        coVerify { dao.marcarInventarioSincronizado("inv-local", "inv-remoto") }
    }

    @Test
    fun `defers the group when the articulo has no remote id yet, without discarding it`() = runTest {
        coEvery { dao.getMovimientosAjustePendientes() } returns listOf(ajuste("mov-1", "art-local"))
        coEvery { dao.getArticulo("art-local") } returns articulo(remoteId = null)
        coEvery { dao.getInventario("suc-1", "art-local") } returns inventario()

        val resultado = pusher.empujarPendientes()

        assertEquals(PushResultado(subidas = 0, saltadas = 1), resultado)
        coVerify(exactly = 0) { api.ajustarArticulo(any(), any()) }
        // Temporal, no irrecuperable: se retoma cuando la entrada del
        // articulo sincronice y obtenga remoteId. No se descarta.
        coVerify(exactly = 0) { dao.marcarMovimientoSincronizadoSinRemoto(any()) }
    }

    @Test
    fun `discards the group when the backend rejects it with a non-recoverable error`() = runTest {
        coEvery { dao.getMovimientosAjustePendientes() } returns listOf(ajuste("mov-1", "art-local"))
        coEvery { dao.getArticulo("art-local") } returns articulo(remoteId = "art-remoto")
        coEvery { dao.getInventario("suc-1", "art-local") } returns inventario()
        coEvery { api.ajustarArticulo(any(), any()) } throws httpException(404)

        val resultado = pusher.empujarPendientes()

        assertEquals(PushResultado(subidas = 0, saltadas = 1), resultado)
        // Sin esto, un ajuste irrecuperable se reintentaria en cada ciclo
        // para siempre (PLAN.md Parte 32, hallazgo de verificacion en
        // dispositivo).
        coVerify(exactly = 1) { dao.marcarMovimientoSincronizadoSinRemoto("mov-1") }
        coVerify(exactly = 0) { dao.marcarArticuloSincronizado(any(), any()) }
    }

    @Test
    fun `marks the movimiento synced without a remote id when the backend delta was zero`() = runTest {
        coEvery { dao.getMovimientosAjustePendientes() } returns listOf(ajuste("mov-1", "art-local"))
        coEvery { dao.getArticulo("art-local") } returns articulo(remoteId = "art-remoto")
        coEvery { dao.getInventario("suc-1", "art-local") } returns inventario()
        coEvery { api.ajustarArticulo(any(), any()) } returns ajusteDto(conMovimiento = false)

        pusher.empujarPendientes()

        coVerify { dao.marcarMovimientoSincronizadoSinRemoto("mov-1") }
    }
}
