package com.pdv.pos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Los montos viajan como String (no Double), misma convencion que
// VentaCreateRequestDto - evita perder precision decimal en el viaje de
// ida y vuelta por JSON.
@Serializable
data class CorteCajaCreateRequestDto(
    @SerialName("local_id") val localId: String? = null,
    @SerialName("sucursal_id") val sucursalId: String,
    @SerialName("usuario_id") val usuarioId: String,
    val tipo: String,
    @SerialName("fecha_inicio") val fechaInicio: String,
    @SerialName("fecha_fin") val fechaFin: String,
    @SerialName("total_ventas") val totalVentas: String,
    @SerialName("total_efectivo") val totalEfectivo: String,
    @SerialName("total_tarjeta") val totalTarjeta: String,
    @SerialName("total_retiros") val totalRetiros: String,
    @SerialName("monto_esperado") val montoEsperado: String,
    @SerialName("monto_contado") val montoContado: String? = null,
    val diferencia: String? = null,
)

@Serializable
data class CorteCajaDto(
    val id: String,
    @SerialName("local_id") val localId: String? = null,
    @SerialName("sucursal_id") val sucursalId: String,
    @SerialName("usuario_id") val usuarioId: String,
    val tipo: String,
    @SerialName("fecha_inicio") val fechaInicio: String,
    @SerialName("fecha_fin") val fechaFin: String,
    @SerialName("total_ventas") val totalVentas: String,
    @SerialName("total_efectivo") val totalEfectivo: String,
    @SerialName("total_tarjeta") val totalTarjeta: String,
    @SerialName("total_retiros") val totalRetiros: String,
    @SerialName("monto_esperado") val montoEsperado: String,
    @SerialName("monto_contado") val montoContado: String? = null,
    val diferencia: String? = null,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("is_synced") val isSynced: Boolean = true,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class TotalesCorteDto(
    @SerialName("total_ventas") val totalVentas: String,
    @SerialName("total_efectivo") val totalEfectivo: String,
    @SerialName("total_tarjeta") val totalTarjeta: String,
    @SerialName("total_retiros") val totalRetiros: String,
    @SerialName("monto_esperado") val montoEsperado: String,
)
