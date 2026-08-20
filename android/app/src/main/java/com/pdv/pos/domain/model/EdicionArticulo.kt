package com.pdv.pos.domain.model

import java.math.BigDecimal

// Edicion de atributos de catalogo + ajuste de cantidad en existencia
// (PLAN.md Parte 9, "modificar los atributos de esos articulos
// consultados" incluye la cantidad, decision del usuario 2026-08-19). La
// cantidad nunca se aplica como UPDATE directo: el repositorio calcula
// delta = nuevaCantidad - cantidad_conocida_localmente y lo aplica via
// EventoAditivoCombiner, generando un movimiento tipo "ajuste" (PLAN.md
// Parte 6, "Decisiones abiertas").
data class EdicionArticulo(
    val articuloId: String,
    val sucursalId: String,
    val usuarioId: String,
    val nombre: String,
    val descripcion: String?,
    val categoria: String?,
    val unidadMedida: String,
    val precioVenta: BigDecimal,
    val costo: BigDecimal?,
    val nuevaCantidad: BigDecimal,
    val ubicacion: String?,
)
