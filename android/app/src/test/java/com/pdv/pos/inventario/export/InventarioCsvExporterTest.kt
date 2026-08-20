package com.pdv.pos.inventario.export

import com.pdv.pos.domain.model.Articulo
import com.pdv.pos.domain.model.InventarioItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class InventarioCsvExporterTest {

    private val item = InventarioItem(
        articulo = Articulo(
            id = "a1",
            sku = "REF-001",
            nombre = "Refresco, edición especial",
            categoria = "Bebidas",
            unidadMedida = "pieza",
            precioVenta = BigDecimal("18.50"),
            costo = BigDecimal("12.00"),
        ),
        cantidad = BigDecimal("24"),
        ubicacion = "Estante A1",
    )

    @Test
    fun `generar produces a header row and one row per item`() {
        val csv = InventarioCsvExporter.generar(listOf(item))
        val lineas = csv.trim().split("\r\n")

        assertEquals("SKU,Nombre,Categoria,Unidad de medida,Cantidad,Ubicacion,Precio de venta,Costo", lineas[0])
        assertEquals(
            "REF-001,\"Refresco, edición especial\",Bebidas,pieza,24.00,Estante A1,18.50,12.00",
            lineas[1],
        )
    }

    @Test
    fun `generar leaves optional null fields empty`() {
        val sinCategoriaNiCosto = item.copy(
            articulo = item.articulo.copy(categoria = null, costo = null),
            ubicacion = null,
        )
        val csv = InventarioCsvExporter.generar(listOf(sinCategoriaNiCosto))
        val fila = csv.trim().split("\r\n")[1]

        assertEquals("REF-001,\"Refresco, edición especial\",,pieza,24.00,,18.50,", fila)
    }
}
