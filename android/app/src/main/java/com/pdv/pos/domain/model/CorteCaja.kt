package com.pdv.pos.domain.model

import java.math.BigDecimal

data class CorteCaja(
    val id: String,
    val sucursalId: String,
    val usuarioId: String,
    val tipo: String,
    val fechaInicio: Long,
    val fechaFin: Long,
    val totalVentas: BigDecimal,
    val totalEfectivo: BigDecimal,
    val totalTarjeta: BigDecimal,
    val totalRetiros: BigDecimal,
    val montoEsperado: BigDecimal,
    val montoContado: BigDecimal?,
    val diferencia: BigDecimal?,
)

data class RetiroEfectivo(
    val id: String,
    val sucursalId: String,
    val usuarioId: String,
    val monto: BigDecimal,
    val motivo: String?,
    val fecha: Long,
)

data class TotalesCorte(
    val totalVentas: BigDecimal,
    val totalEfectivo: BigDecimal,
    val totalTarjeta: BigDecimal,
    val totalRetiros: BigDecimal,
    val montoEsperado: BigDecimal,
)
