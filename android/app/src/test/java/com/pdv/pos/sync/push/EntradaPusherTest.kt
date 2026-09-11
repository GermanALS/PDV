package com.pdv.pos.sync.push

import com.pdv.pos.data.local.ArticuloEntity
import com.pdv.pos.data.local.EntradaDao
import com.pdv.pos.data.local.MovimientoEntity
import com.pdv.pos.data.remote.EntradaApiService
import com.pdv.pos.data.remote.dto.ArticuloDto
import com.pdv.pos.data.remote.dto.EntradaDto
import com.pdv.pos.data.remote.dto.InventarioDto
import com.pdv.pos.data.remote.dto.MovimientoDto
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

class EntradaPusherTest {

    private val dao = mockk<EntradaDao>(relaxUnitFun = true)
    private val api = mockk<EntradaApiService>()
    private val appLogger = mockk<AppLogger>(relaxed = true)
    private val pusher = EntradaPusher(dao, api, appLogger)

    private fun movimiento() = MovimientoEntity(
        localId = "mov-1",
        remoteId = null,
        sucursalId = "suc-1",
        articuloId = "art-local",
        usuarioId = "u-1",
        tipo = "entrada",
        cantidad = BigDecimal("5"),
        ubicacion = null,
        referenciaTipo = "entrada_manual",
        referenciaId = "entrada-1",
        fecha = 1_700_000_000_000L,
        updatedAt = 0L,
        isSynced = false,
        deletedAt = null,
    )

    private fun articulo(remoteId: String?) = ArticuloEntity(
        localId = "art-local",
        remoteId = remoteId,
        codigoBarras = null,
        sku = "SKU-1",
        nombre = "Cafe",
        descripcion = null,
        categoria = null,
        unidadMedida = "unidad",
        precioVenta = BigDecimal("30"),
        costo = null,
        activo = true,
        updatedAt = 0L,
        isSynced = remoteId != null,
        deletedAt = null,
    )

    private fun entradaDto(movId: String, articuloId: String?) = EntradaDto(
        movimiento = MovimientoDto(
            id = movId,
            sucursalId = "suc-1",
            articuloId = "art-remoto",
            usuarioId = "u-1",
            tipo = "entrada",
            cantidad = "5",
            fecha = "2023-11-14T22:13:20Z",
            updatedAt = "2023-11-14T22:13:20Z",
        ),
        inventario = InventarioDto(
            id = "inv-1",
            sucursalId = "suc-1",
            articuloId = "art-remoto",
            cantidad = "5",
            updatedAt = "2023-11-14T22:13:20Z",
        ),
        articulo = articuloId?.let {
            ArticuloDto(
                id = it,
                sku = "SKU-1",
                nombre = "Cafe",
                unidadMedida = "unidad",
                precioVenta = "30",
                updatedAt = "2023-11-14T22:13:20Z",
            )
        },
    )

    @Test
    fun `a new articulo is pushed as articulo_nuevo and its remote id is persisted`() = runTest {
        coEvery { dao.getMovimientosEntradaPendientes() } returns listOf(movimiento())
        coEvery { dao.getArticulo("art-local") } returns articulo(remoteId = null)
        val request = slot<com.pdv.pos.data.remote.dto.EntradaCreateRequestDto>()
        coEvery { api.createEntrada(capture(request)) } returns entradaDto(movId = "mov-remoto", articuloId = "art-remoto")

        val resultado = pusher.empujarPendientes()

        assertEquals(PushResultado(subidas = 1, saltadas = 0), resultado)
        assertNull(request.captured.articuloId)
        assertEquals("SKU-1", request.captured.articuloNuevo?.sku)
        assertEquals("entrada-1", request.captured.localId)
        coVerify { dao.marcarMovimientoSincronizado("mov-1", "mov-remoto") }
        coVerify { dao.marcarArticuloSincronizado("art-local", "art-remoto") }
    }

    @Test
    fun `an already-synced articulo is referenced by its remote id`() = runTest {
        coEvery { dao.getMovimientosEntradaPendientes() } returns listOf(movimiento())
        coEvery { dao.getArticulo("art-local") } returns articulo(remoteId = "art-remoto-7")
        val request = slot<com.pdv.pos.data.remote.dto.EntradaCreateRequestDto>()
        coEvery { api.createEntrada(capture(request)) } returns entradaDto(movId = "mov-remoto", articuloId = null)

        pusher.empujarPendientes()

        assertEquals("art-remoto-7", request.captured.articuloId)
        assertNull(request.captured.articuloNuevo)
        coVerify { dao.marcarMovimientoSincronizado("mov-1", "mov-remoto") }
    }
}
