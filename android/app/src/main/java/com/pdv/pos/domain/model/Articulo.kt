package com.pdv.pos.domain.model

import java.math.BigDecimal

data class Articulo(
    val id: String,
    val codigoBarras: String? = null,
    val sku: String,
    val nombre: String,
    val descripcion: String? = null,
    val categoria: String? = null,
    val unidadMedida: String,
    val precioVenta: BigDecimal,
    val costo: BigDecimal? = null,
    val activo: Boolean = true,
)
