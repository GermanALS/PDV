package com.pdv.pos.inventario.export

import com.pdv.pos.domain.model.InventarioItem
import com.pdv.pos.inventario.formatoCantidad
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Genera un .xlsx minimo a mano (PLAN.md Parte 9, ampliacion aprobada
// explicitamente por el usuario): un XLSX es un zip de XML simple, y esta
// exportacion no necesita formulas ni estilos, asi que se evita una
// dependencia de terceros (ej. Apache POI, pesada y con problemas conocidos
// de compatibilidad en Android) a cambio de este escritor acotado.
object InventarioExcelExporter {

    private val ENCABEZADOS = listOf(
        "SKU", "Nombre", "Categoria", "Unidad de medida", "Cantidad", "Ubicacion", "Precio de venta", "Costo",
    )

    // Cantidad, Precio de venta y Costo son numericas (columnas E, G, H); el
    // resto se escribe como texto inline (sin tabla de shared strings).
    private val COLUMNAS_NUMERICAS = setOf(4, 6, 7)

    fun generar(items: List<InventarioItem>): ByteArray {
        val filas = items.map { it.toFila() }
        val hoja = construirHojaXml(listOf(ENCABEZADOS) + filas)

        val salida = ByteArrayOutputStream()
        ZipOutputStream(salida).use { zip ->
            zip.escribirEntrada("[Content_Types].xml", CONTENT_TYPES_XML)
            zip.escribirEntrada("_rels/.rels", RELS_XML)
            zip.escribirEntrada("xl/workbook.xml", WORKBOOK_XML)
            zip.escribirEntrada("xl/_rels/workbook.xml.rels", WORKBOOK_RELS_XML)
            zip.escribirEntrada("xl/worksheets/sheet1.xml", hoja)
        }
        return salida.toByteArray()
    }

    private fun InventarioItem.toFila(): List<String> = listOf(
        articulo.sku,
        articulo.nombre,
        articulo.categoria.orEmpty(),
        articulo.unidadMedida,
        cantidad.formatoCantidad(),
        ubicacion.orEmpty(),
        articulo.precioVenta.toPlainString(),
        articulo.costo?.toPlainString().orEmpty(),
    )

    private fun construirHojaXml(filas: List<List<String>>): String {
        val filasXml = filas.mapIndexed { indiceFila, fila ->
            val numeroFila = indiceFila + 1
            val celdas = fila.mapIndexed { indiceColumna, valor ->
                val referencia = "${columnaLetra(indiceColumna)}$numeroFila"
                if (indiceColumna in COLUMNAS_NUMERICAS && valor.isNotEmpty()) {
                    """<c r="$referencia"><v>${valor.escaparXml()}</v></c>"""
                } else {
                    """<c r="$referencia" t="inlineStr"><is><t xml:space="preserve">${valor.escaparXml()}</t></is></c>"""
                }
            }.joinToString("")
            """<row r="$numeroFila">$celdas</row>"""
        }.joinToString("")

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<sheetData>$filasXml</sheetData>
</worksheet>"""
    }

    private fun columnaLetra(indiceBaseCero: Int): String {
        var n = indiceBaseCero + 1
        val letras = StringBuilder()
        while (n > 0) {
            val resto = (n - 1) % 26
            letras.insert(0, ('A' + resto))
            n = (n - 1) / 26
        }
        return letras.toString()
    }

    private fun String.escaparXml(): String = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun ZipOutputStream.escribirEntrada(nombre: String, contenido: String) {
        putNextEntry(ZipEntry(nombre))
        write(contenido.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private const val CONTENT_TYPES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

    private const val RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private const val WORKBOOK_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets>
<sheet name="Inventario" sheetId="1" r:id="rId1"/>
</sheets>
</workbook>"""

    private const val WORKBOOK_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""
}
