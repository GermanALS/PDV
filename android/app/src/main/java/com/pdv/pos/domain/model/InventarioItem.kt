package com.pdv.pos.domain.model

import java.math.BigDecimal

// Fila de inventario (docs/schema-pos.json) junto con el articulo de
// catalogo al que pertenece - la pantalla de Inventario (PLAN.md Parte 9)
// siempre consulta y edita ambos juntos.
data class InventarioItem(
    val articulo: Articulo,
    val cantidad: BigDecimal,
    val ubicacion: String?,
)
