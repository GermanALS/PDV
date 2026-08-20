package com.pdv.pos.venta.ticket

import com.pdv.pos.venta.LineaCarrito
import com.pdv.pos.venta.MetodoPago
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Formateador puro (sin dependencias de Android) de las lineas de texto del
// ticket, separado de TicketPdfWriter (dibuja en un PdfDocument) siguiendo
// el mismo criterio que InventarioCsvExporter/InventarioExcelExporter:
// la logica de contenido es testeable con JUnit5 sin Robolectric.
object TicketFormatter {

    private val formatoFecha = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun generarLineas(
        folio: String,
        fecha: Long,
        sucursalNombre: String,
        sucursalDireccion: String?,
        usuarioId: String,
        carrito: List<LineaCarrito>,
        subtotal: BigDecimal,
        descuento: BigDecimal,
        impuestos: BigDecimal,
        total: BigDecimal,
        metodoPago: MetodoPago,
        efectivoRecibido: BigDecimal?,
        cambio: BigDecimal?,
    ): List<String> {
        val lineas = mutableListOf<String>()
        lineas += sucursalNombre
        sucursalDireccion?.let { lineas += it }
        lineas += "Folio: $folio"
        lineas += "Fecha: ${formatoFecha.format(Date(fecha))}"
        lineas += "Atendio: $usuarioId"
        lineas += "-".repeat(32)
        carrito.forEach { linea ->
            lineas += linea.articulo.nombre
            lineas += "  ${linea.cantidad} x ${linea.articulo.precioVenta} = ${linea.subtotal}"
        }
        lineas += "-".repeat(32)
        lineas += "Subtotal: $subtotal"
        if (descuento > BigDecimal.ZERO) lineas += "Descuento: $descuento"
        if (impuestos > BigDecimal.ZERO) lineas += "Impuestos: $impuestos"
        lineas += "Total: $total"
        lineas += "Metodo de pago: ${metodoPago.etiquetaTicket()}"
        if (efectivoRecibido != null && cambio != null) {
            lineas += "Efectivo recibido: $efectivoRecibido"
            lineas += "Cambio: $cambio"
        }
        lineas += "-".repeat(32)
        lineas += "Gracias por su compra"
        return lineas
    }

    private fun MetodoPago.etiquetaTicket(): String = when (this) {
        MetodoPago.EFECTIVO -> "Efectivo"
        MetodoPago.TARJETA -> "Tarjeta"
    }
}
