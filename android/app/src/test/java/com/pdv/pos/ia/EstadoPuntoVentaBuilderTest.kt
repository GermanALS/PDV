package com.pdv.pos.ia

import com.pdv.pos.data.remote.dto.ChatMessageDto
import com.pdv.pos.domain.model.Articulo
import com.pdv.pos.domain.model.InventarioItem
import com.pdv.pos.domain.model.PaginaInventario
import com.pdv.pos.domain.model.Sucursal
import com.pdv.pos.domain.model.TotalesCorte
import com.pdv.pos.domain.repository.CajaRepository
import com.pdv.pos.domain.repository.InventarioRepository
import com.pdv.pos.domain.repository.SucursalRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class EstadoPuntoVentaBuilderTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun itemDeEjemplo() = InventarioItem(
        articulo = Articulo(
            id = "art-1",
            sku = "REF-001",
            nombre = "Refresco de cola 600ml",
            unidadMedida = "pieza",
            precioVenta = BigDecimal("18.50"),
            costo = BigDecimal("12.00"),
        ),
        cantidad = BigDecimal("24"),
        ubicacion = "Pasillo 3",
    )

    private fun builder(
        sucursales: List<Sucursal> = listOf(Sucursal(id = "suc-1", nombre = "Sucursal Centro")),
        totales: TotalesCorte = TotalesCorte(
            totalVentas = BigDecimal("1500.00"),
            totalEfectivo = BigDecimal("900.00"),
            totalTarjeta = BigDecimal("600.00"),
            totalRetiros = BigDecimal("100.00"),
            montoEsperado = BigDecimal("800.00"),
        ),
        items: List<InventarioItem> = listOf(itemDeEjemplo()),
    ): EstadoPuntoVentaBuilder {
        val sucursalRepository = mockk<SucursalRepository>()
        every { sucursalRepository.observeSucursales() } returns flowOf(sucursales)

        val cajaRepository = mockk<CajaRepository>()
        coEvery { cajaRepository.calcularTotales(any(), any(), any()) } returns totales

        val inventarioRepository = mockk<InventarioRepository>()
        every {
            inventarioRepository.observarInventario(any(), any(), any(), any())
        } returns flowOf(PaginaInventario(items = items, pagina = 1, tamanioPagina = 50, total = items.size))

        return EstadoPuntoVentaBuilder(sucursalRepository, inventarioRepository, cajaRepository, json)
    }

    @Test
    fun `armarJson produce el shape esperado con sucursal, caja, inventario e historial`() = runTest {
        val historial = listOf(
            ChatMessageDto(role = "user", content = "cuanto tengo en caja"),
            ChatMessageDto(role = "assistant", content = "tenes 800 esperados"),
        )

        val resultado = builder().armarJson(sucursalId = "suc-1", historial = historial)
        val raiz = json.parseToJsonElement(resultado).jsonObject

        assertEquals("suc-1", raiz["sucursalId"]?.jsonPrimitive?.content)
        assertEquals("Sucursal Centro", raiz["sucursalNombre"]?.jsonPrimitive?.content)

        val caja = raiz["caja"]?.jsonObject
        assertEquals("1500.00", caja?.get("totalVentas")?.jsonPrimitive?.content)
        assertEquals("800.00", caja?.get("montoEsperado")?.jsonPrimitive?.content)

        val inventario = raiz["inventario"]?.jsonArray
        assertEquals(1, inventario?.size)
        val articulo = inventario!!.first().jsonObject
        assertEquals("REF-001", articulo["sku"]?.jsonPrimitive?.content)
        assertEquals("24", articulo["cantidad"]?.jsonPrimitive?.content)
        assertEquals("12.00", articulo["costo"]?.jsonPrimitive?.content)

        val historialJson = raiz["historial"]?.jsonArray
        assertEquals(2, historialJson?.size)
        assertEquals("user", historialJson!!.first().jsonObject["role"]?.jsonPrimitive?.content)
    }

    @Test
    fun `armarJson tolera un articulo sin costo ni ubicacion`() = runTest {
        val itemSinCosto = InventarioItem(
            articulo = Articulo(
                id = "art-2",
                sku = "SRV-002",
                nombre = "Servicio de instalacion",
                unidadMedida = "pieza",
                precioVenta = BigDecimal("50.00"),
                costo = null,
            ),
            cantidad = BigDecimal("0"),
            ubicacion = null,
        )

        val resultado = builder(items = listOf(itemSinCosto)).armarJson(sucursalId = "suc-1", historial = emptyList())
        val articulo = json.parseToJsonElement(resultado).jsonObject["inventario"]!!.jsonArray.first().jsonObject

        assertTrue(articulo["costo"] is kotlinx.serialization.json.JsonNull)
        assertTrue(articulo["ubicacion"] is kotlinx.serialization.json.JsonNull)
    }

    @Test
    fun `armarJson deja sucursalNombre en null si la sucursal no esta en el catalogo`() = runTest {
        val resultado = builder(sucursales = emptyList()).armarJson(sucursalId = "suc-inexistente", historial = emptyList())
        val raiz = json.parseToJsonElement(resultado).jsonObject

        assertTrue(raiz["sucursalNombre"] is kotlinx.serialization.json.JsonNull)
    }
}
