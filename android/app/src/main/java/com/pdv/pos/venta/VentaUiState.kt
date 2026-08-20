package com.pdv.pos.venta

import com.pdv.pos.domain.model.Articulo
import java.io.File
import java.math.BigDecimal

enum class MetodoPago {
    EFECTIVO,
    TARJETA,
}

data class LineaCarrito(
    val articulo: Articulo,
    val cantidad: Int,
) {
    val subtotal: BigDecimal = articulo.precioVenta * BigDecimal(cantidad)
}

data class VentaUiState(
    val busqueda: String = "",
    val articuloEncontrado: Articulo? = null,
    val errorBusqueda: String? = null,
    val carrito: List<LineaCarrito> = emptyList(),
    val metodoPago: MetodoPago = MetodoPago.EFECTIVO,
    val mostrarEscaner: Boolean = false,
    val mensajeConfirmacion: String? = null,
    val mostrarDialogoEfectivo: Boolean = false,
    val efectivoIngresado: String = "",
    val errorEfectivo: String? = null,
    val cambioEntregado: BigDecimal? = null,
    val ticketPdf: File? = null,
    val mostrarDialogoReimpresion: Boolean = false,
) {
    val subtotal: BigDecimal = carrito.fold(BigDecimal.ZERO) { acumulado, linea -> acumulado + linea.subtotal }
    val descuento: BigDecimal = BigDecimal.ZERO
    val impuestos: BigDecimal = BigDecimal.ZERO
    val total: BigDecimal = subtotal - descuento + impuestos
}
