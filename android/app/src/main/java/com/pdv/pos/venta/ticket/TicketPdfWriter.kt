package com.pdv.pos.venta.ticket

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument

// Dibuja las lineas de TicketFormatter en un PdfDocument de una sola pagina,
// ancho fijo tipo recibo termico (80mm ~ 227pt) y alto dinamico segun la
// cantidad de lineas. Glue de Android sin test unitario (mismo criterio que
// BarcodeScannerDialog/InventarioExportManager: se testea el contenido puro,
// no el trazo en Canvas).
object TicketPdfWriter {

    private const val ANCHO_PT = 227
    private const val MARGEN_PT = 12f
    private const val ALTO_LINEA_PT = 16f
    private const val TAMANO_FUENTE_PT = 10f

    fun generar(lineas: List<String>): PdfDocument {
        val alto = (MARGEN_PT * 2 + lineas.size * ALTO_LINEA_PT).toInt().coerceAtLeast(100)
        val documento = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(ANCHO_PT, alto, 1).create()
        val pagina = documento.startPage(pageInfo)
        val paint = Paint().apply {
            textSize = TAMANO_FUENTE_PT
            typeface = Typeface.MONOSPACE
        }
        var y = MARGEN_PT + TAMANO_FUENTE_PT
        lineas.forEach { linea ->
            pagina.canvas.drawText(linea, MARGEN_PT, y, paint)
            y += ALTO_LINEA_PT
        }
        documento.finishPage(pagina)
        return documento
    }
}
