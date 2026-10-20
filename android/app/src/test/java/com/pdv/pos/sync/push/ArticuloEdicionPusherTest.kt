package com.pdv.pos.sync.push

import com.pdv.pos.data.local.ArticuloEntity
import com.pdv.pos.data.local.InventarioDao
import com.pdv.pos.data.local.InventarioEntity
import com.pdv.pos.data.remote.InventarioApiService
import com.pdv.pos.data.remote.dto.ArticuloCatalogoDto
import com.pdv.pos.data.remote.dto.ArticuloCatalogoRequestDto
import com.pdv.pos.data.remote.dto.ArticuloDto
import com.pdv.pos.data.remote.dto.InventarioDto
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.sync.EventoAditivoSyncEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ArticuloEdicionPusherTest {

    private val dao = mockk<InventarioDao>(relaxUnitFun = true)
    private val api = mockk<InventarioApiService>()
    private val engine = mockk<EventoAditivoSyncEngine>(relaxed = true)
    private val appLogger = mockk<AppLogger>(relaxed = true)
    private val pusher = ArticuloEdicionPusher(dao, api, engine, appLogger, registroRechazos())

    private fun articulo(remoteId: String?) = ArticuloEntity(
        localId = "art-local", remoteId = remoteId, codigoBarras = null, sku = "SKU-1", nombre = "Cafe molido",
        descripcion = null, categoria = "Abarrotes", unidadMedida = "pieza", precioVenta = BigDecimal("32.50"),
        costo = BigDecimal("20.00"), activo = true, updatedAt = 1_700_000_000_000L, isSynced = false, deletedAt = null,
    )

    private fun inventario() = InventarioEntity(
        localId = "inv-local", remoteId = null, sucursalId = "suc-1", articuloId = "art-local",
        cantidad = BigDecimal("8"), cantidadNum = 8.0, ubicacion = "Estante A1", updatedAt = 0L,
        isSynced = false, deletedAt = null, edicionPendiente = true,
    )

    private fun respuesta(aplicado: Boolean, nombre: String = "Cafe molido", precio: String = "32.50") =
        ArticuloCatalogoDto(
            aplicado = aplicado,
            articulo = ArticuloDto(
                id = "art-remoto", sku = "SKU-1", nombre = nombre, unidadMedida = "pieza", precioVenta = precio,
                categoria = "Abarrotes", updatedAt = "2023-11-15T10:00:00Z",
            ),
            inventario = InventarioDto(
                id = "inv-remoto", sucursalId = "suc-1", articuloId = "art-remoto", cantidad = "8",
                ubicacion = if (aplicado) "Estante A1" else "Estante Z9", updatedAt = "2023-11-15T10:00:00Z",
            ),
        )

    @Test
    fun `pushes the edit with the catalog fields, the ubicacion and the local edit time`() = runTest {
        coEvery { dao.getEdicionesPendientes() } returns listOf(inventario())
        coEvery { dao.getArticulo("art-local") } returns articulo("art-remoto")
        val request = slot<ArticuloCatalogoRequestDto>()
        coEvery { api.editarCatalogo("art-remoto", capture(request)) } returns respuesta(aplicado = true)

        val resultado = pusher.empujarPendientes()

        assertEquals(PushResultado(subidas = 1, saltadas = 0), resultado)
        assertEquals("suc-1", request.captured.sucursalId)
        assertEquals("Cafe molido", request.captured.nombre)
        assertEquals("32.50", request.captured.precioVenta)
        assertEquals("Estante A1", request.captured.ubicacion)
        assertEquals("2023-11-14T22:13:20Z", request.captured.editadoEn)
        coVerify { dao.cerrarEdicionSubida("inv-local", "art-local", 1_700_000_000_000L, null, null) }
        coVerify(exactly = 0) { engine.registrarEdicionCatalogoPerdida(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `adopts the backend state and records a conflict when the local edit loses`() = runTest {
        coEvery { dao.getEdicionesPendientes() } returns listOf(inventario())
        coEvery { dao.getArticulo("art-local") } returns articulo("art-remoto")
        coEvery { api.editarCatalogo(any(), any()) } returns
            respuesta(aplicado = false, nombre = "Cafe tostado", precio = "35.00")
        val vigente = slot<ArticuloEntity>()
        coEvery { dao.cerrarEdicionSubida(any(), any(), any(), capture(vigente), any()) } returns Unit

        val resultado = pusher.empujarPendientes()

        assertEquals(PushResultado(subidas = 1, saltadas = 0), resultado)
        assertEquals("Cafe tostado", vigente.captured.nombre)
        assertEquals(0, BigDecimal("35.00").compareTo(vigente.captured.precioVenta))
        coVerify { dao.cerrarEdicionSubida("inv-local", "art-local", 1_700_000_000_000L, any(), "Estante Z9") }
        coVerify {
            engine.registrarEdicionCatalogoPerdida(
                articuloLocalId = "art-local",
                sucursalId = "suc-1",
                usuario = "system",
                resumenLocal = "nombre=Cafe molido precio=32.50 ubicacion=Estante A1",
                resumenRemoto = "nombre=Cafe tostado precio=35.00 ubicacion=Estante Z9",
            )
        }
    }

    @Test
    fun `defers the edit when the articulo has no remote id yet, keeping it pending`() = runTest {
        coEvery { dao.getEdicionesPendientes() } returns listOf(inventario())
        coEvery { dao.getArticulo("art-local") } returns articulo(remoteId = null)

        val resultado = pusher.empujarPendientes()

        assertEquals(PushResultado(subidas = 0, saltadas = 1), resultado)
        coVerify(exactly = 0) { api.editarCatalogo(any(), any()) }
        coVerify(exactly = 0) { dao.cerrarEdicionSubida(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `closes the edit and marks it rejected when the backend rejects it with a non-recoverable error`() = runTest {
        coEvery { dao.getEdicionesPendientes() } returns listOf(inventario())
        coEvery { dao.getArticulo("art-local") } returns articulo("art-remoto")
        coEvery { api.editarCatalogo(any(), any()) } throws httpException(404)

        val resultado = pusher.empujarPendientes()

        assertEquals(PushResultado(subidas = 0, saltadas = 1), resultado)
        coVerify(exactly = 1) { dao.cerrarEdicionSubida("inv-local", "art-local", 1_700_000_000_000L, null, null) }
        coVerify(exactly = 1) { dao.marcarRechazoEdicion("inv-local", 404) }
    }
}
