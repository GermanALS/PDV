package com.pdv.pos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Los montos/cantidades viajan como String (no Double) para no perder
// precision decimal en la ida y vuelta por JSON - misma convencion que
// VentaDetalleCreateRequestDto.
@Serializable
data class DevolucionDetalleCreateRequestDto(
    @SerialName("local_id") val localId: String? = null,
    @SerialName("articulo_id") val articuloId: String,
    val cantidad: String,
    val motivo: String? = null,
    val condicion: String? = null,
)

@Serializable
data class DevolucionDetalleDto(
    val id: String,
    @SerialName("local_id") val localId: String? = null,
    @SerialName("articulo_id") val articuloId: String,
    val cantidad: String,
    val motivo: String? = null,
    val condicion: String? = null,
)

@Serializable
data class DevolucionCreateRequestDto(
    @SerialName("local_id") val localId: String? = null,
    @SerialName("sucursal_id") val sucursalId: String,
    @SerialName("usuario_id") val usuarioId: String,
    @SerialName("venta_id") val ventaId: String? = null,
    val folio: String,
    val fecha: String,
    val estado: String,
    val lineas: List<DevolucionDetalleCreateRequestDto>,
)

@Serializable
data class DevolucionDto(
    val id: String,
    @SerialName("local_id") val localId: String? = null,
    @SerialName("sucursal_id") val sucursalId: String,
    @SerialName("usuario_id") val usuarioId: String,
    @SerialName("venta_id") val ventaId: String? = null,
    val folio: String,
    val fecha: String,
    val estado: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("is_synced") val isSynced: Boolean = true,
    @SerialName("deleted_at") val deletedAt: String? = null,
    val lineas: List<DevolucionDetalleDto>,
)
