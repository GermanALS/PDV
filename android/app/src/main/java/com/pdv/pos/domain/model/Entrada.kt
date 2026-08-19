package com.pdv.pos.domain.model

import java.math.BigDecimal

data class ArticuloNuevo(
    val id: String,
    val codigoBarras: String?,
    val sku: String,
    val nombre: String,
    val descripcion: String?,
    val categoria: String?,
    val unidadMedida: String,
    val precioVenta: BigDecimal,
    val costo: BigDecimal?,
)

// Dos variantes por diseño (PLAN.md Parte 8): un articulo nuevo trae sus
// datos de catalogo completos, uno existente solo referencia su id - evita
// un modelo con campos opcionales ambiguos.
sealed class Entrada {
    abstract val id: String
    abstract val sucursalId: String
    abstract val usuarioId: String
    abstract val fecha: Long
    abstract val cantidad: BigDecimal
    abstract val ubicacion: String?

    data class DeArticuloNuevo(
        override val id: String,
        override val sucursalId: String,
        override val usuarioId: String,
        override val fecha: Long,
        override val cantidad: BigDecimal,
        override val ubicacion: String?,
        val articulo: ArticuloNuevo,
    ) : Entrada()

    data class DeArticuloExistente(
        override val id: String,
        override val sucursalId: String,
        override val usuarioId: String,
        override val fecha: Long,
        override val cantidad: BigDecimal,
        override val ubicacion: String?,
        val articuloId: String,
    ) : Entrada()
}
