package com.pdv.pos.domain.model

import java.math.BigDecimal

data class DevolucionLinea(
    val articuloId: String,
    val cantidad: BigDecimal,
    val motivo: String?,
    val condicion: String?,
)

data class Devolucion(
    val id: String,
    val sucursalId: String,
    val usuarioId: String,
    val ventaId: String?,
    val folio: String,
    val fecha: Long,
    val estado: String,
    val lineas: List<DevolucionLinea>,
)
