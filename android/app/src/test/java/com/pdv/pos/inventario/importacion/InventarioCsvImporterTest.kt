package com.pdv.pos.inventario.importacion

import com.pdv.pos.domain.model.Articulo
import com.pdv.pos.domain.model.InventarioItem
import com.pdv.pos.inventario.export.InventarioCsvExporter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class InventarioCsvImporterTest {

    private val encabezado = "SKU,Nombre,Categoria,Unidad de medida,Cantidad,Ubicacion,Precio de venta,Costo"

    @Test
    fun `encabezado invalido aborta sin procesar ninguna fila`() {
        val csv = "SKU,Nombre,Categoria\nREF-001,Refresco,Bebidas"

        val resultado = InventarioCsvImporter.parsear(csv)

        assertEquals(false, resultado.encabezadoValido)
        assertTrue(resultado.filas.isEmpty())
    }

    @Test
    fun `un csv sin filas de datos es encabezado valido con lista vacia`() {
        val resultado = InventarioCsvImporter.parsear(encabezado)

        assertTrue(resultado.encabezadoValido)
        assertTrue(resultado.filas.isEmpty())
    }

    @Test
    fun `mezcla de filas validas e invalidas - las validas se parsean y las invalidas quedan listadas`() {
        val csv = """
            $encabezado
            REF-001,Refresco de cola,Bebidas,pieza,10,Estante A1,18.50,12.00
            PAN-002,Pan integral,Panaderia,pieza,texto,Estante B2,42.00,
            LEC-003,Leche entera,,pieza,5,Estante C1,27.90,
        """.trimIndent()

        val resultado = InventarioCsvImporter.parsear(csv)

        assertTrue(resultado.encabezadoValido)
        assertEquals(3, resultado.filas.size)

        val valida = resultado.filas[0] as FilaCsvImportada.Valida
        assertEquals("REF-001", valida.datos.sku)
        assertEquals(BigDecimal("10"), valida.datos.cantidad)
        assertEquals(BigDecimal("18.50"), valida.datos.precioVenta)
        assertEquals(BigDecimal("12.00"), valida.datos.costo)

        val cantidadInvalida = resultado.filas[1] as FilaCsvImportada.Invalida
        assertEquals(3, cantidadInvalida.numeroFila)
        assertTrue(cantidadInvalida.motivo.contains("Cantidad"))

        val categoriaVacia = resultado.filas[2] as FilaCsvImportada.Invalida
        assertEquals(4, categoriaVacia.numeroFila)
    }

    @Test
    fun `costo vacio se parsea como null`() {
        val csv = "$encabezado\nREF-001,Refresco,Bebidas,pieza,10,Estante A1,18.50,"

        val fila = InventarioCsvImporter.parsear(csv).filas.single() as FilaCsvImportada.Valida

        assertNull(fila.datos.costo)
    }

    @Test
    fun `fila con menos columnas de las esperadas es invalida`() {
        val csv = "$encabezado\nREF-001,Refresco,Bebidas"

        val fila = InventarioCsvImporter.parsear(csv).filas.single() as FilaCsvImportada.Invalida

        assertTrue(fila.motivo.contains("columnas"))
    }

    // Simetria con InventarioCsvExporter: lo que exporta un item con coma y
    // comillas en el nombre debe volver a parsearse igual (PLAN.md Parte 18,
    // sub-parte A - mismo formato en ambas direcciones).
    @Test
    fun `un csv exportado con campos entre comillas se vuelve a parsear igual`() {
        val item = InventarioItem(
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
        val csv = InventarioCsvExporter.generar(listOf(item))

        val fila = InventarioCsvImporter.parsear(csv).filas.single() as FilaCsvImportada.Valida

        assertEquals("Refresco, edición especial", fila.datos.nombre)
        assertEquals(BigDecimal("24.00"), fila.datos.cantidad)
    }
}
