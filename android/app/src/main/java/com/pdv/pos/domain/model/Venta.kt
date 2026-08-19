package com.pdv.pos.domain.model

import java.math.BigDecimal

data class VentaLinea(
    val articuloId: String,
    val cantidad: BigDecimal,
    val precioUnitario: BigDecimal,
    val subtotal: BigDecimal,
)

data class Venta(
    val id: String,
    val sucursalId: String,
    val usuarioId: String,
    val folio: String,
    val fecha: Long,
    val subtotal: BigDecimal,
    val descuento: BigDecimal,
    val impuestos: BigDecimal,
    val total: BigDecimal,
    val metodoPago: String,
    val estado: String,
    val lineas: List<VentaLinea>,
)
