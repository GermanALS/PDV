package com.pdv.pos.inventario.export

import com.pdv.pos.domain.model.Articulo
import com.pdv.pos.domain.model.InventarioItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class InventarioExcelExporterTest {

    private val item = InventarioItem(
        articulo = Articulo(
            id = "a1",
            sku = "REF-001",
            nombre = "Refresco & Cía",
            categoria = "Bebidas",
            unidadMedida = "pieza",
            precioVenta = BigDecimal("18.50"),
            costo = BigDecimal("12.00"),
        ),
        cantidad = BigDecimal("24"),
        ubicacion = "Estante A1",
    )

    @Test
    fun `generar produces a valid zip with the required OOXML parts`() {
        val bytes = InventarioExcelExporter.generar(listOf(item))
        val nombres = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entrada = zip.nextEntry
            while (entrada != null) {
                nombres.add(entrada.name)
                entrada = zip.nextEntry
            }
        }

        assertTrue(nombres.contains("[Content_Types].xml"))
        assertTrue(nombres.contains("_rels/.rels"))
        assertTrue(nombres.contains("xl/workbook.xml"))
        assertTrue(nombres.contains("xl/_rels/workbook.xml.rels"))
        assertTrue(nombres.contains("xl/worksheets/sheet1.xml"))
    }

    @Test
    fun `generar writes a well-formed worksheet with header and data rows`() {
        val bytes = InventarioExcelExporter.generar(listOf(item))
        val hojaXml = leerEntrada(bytes, "xl/worksheets/sheet1.xml")

        val documento = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(ByteArrayInputStream(hojaXml.toByteArray(Charsets.UTF_8)))
        val filas = documento.getElementsByTagName("row")

        assertEquals(2, filas.length)

        val primeraCeldaEncabezado = (filas.item(0) as org.w3c.dom.Element)
            .getElementsByTagName("t").item(0).textContent
        assertEquals("SKU", primeraCeldaEncabezado)

        assertTrue(hojaXml.contains("Refresco &amp; Cía"))
        assertTrue(hojaXml.contains("<c r=\"E2\"><v>24.00</v></c>"))
    }

    private fun leerEntrada(bytes: ByteArray, nombre: String): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entrada = zip.nextEntry
            while (entrada != null) {
                if (entrada.name == nombre) {
                    return zip.readBytes().toString(Charsets.UTF_8)
                }
                entrada = zip.nextEntry
            }
        }
        throw AssertionError("Entrada no encontrada en el zip: $nombre")
    }
}
