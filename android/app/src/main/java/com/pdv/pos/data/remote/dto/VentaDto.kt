package com.pdv.pos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Los montos viajan como String (no Double) para no perder precision decimal
// en la ida y vuelta por JSON; el backend (Pydantic Decimal) los acepta
// igual que los numeros crudos, ya verificado en backend/tests/test_ventas.py.
@Serializable
data class VentaDetalleCreateRequestDto(
    @SerialName("local_id") val localId: String? = null,
    @SerialName("articulo_id") val articuloId: String,
    val cantidad: String,
    @SerialName("precio_unitario") val precioUnitario: String,
    val subtotal: String,
)

@Serializable
data class VentaDetalleDto(
    val id: String,
    @SerialName("local_id") val localId: String? = null,
    @SerialName("articulo_id") val articuloId: String,
    val cantidad: String,
    @SerialName("precio_unitario") val precioUnitario: String,
    val subtotal: String,
)

@Serializable
data class VentaCreateRequestDto(
    @SerialName("local_id") val localId: String? = null,
    @SerialName("sucursal_id") val sucursalId: String,
    @SerialName("usuario_id") val usuarioId: String,
    val folio: String,
    val fecha: String,
    val subtotal: String,
    val descuento: String,
    val impuestos: String,
    val total: String,
    @SerialName("metodo_pago") val metodoPago: String,
    val estado: String,
    val lineas: List<VentaDetalleCreateRequestDto>,
)

@Serializable
data class VentaDto(
    val id: String,
    @SerialName("local_id") val localId: String? = null,
    @SerialName("sucursal_id") val sucursalId: String,
    @SerialName("usuario_id") val usuarioId: String,
    val folio: String,
    val fecha: String,
    val subtotal: String,
    val descuento: String,
    val impuestos: String,
    val total: String,
    @SerialName("metodo_pago") val metodoPago: String,
    val estado: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("is_synced") val isSynced: Boolean = true,
    @SerialName("deleted_at") val deletedAt: String? = null,
    val lineas: List<VentaDetalleDto>,
)
