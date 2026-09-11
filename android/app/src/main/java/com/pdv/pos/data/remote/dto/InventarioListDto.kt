package com.pdv.pos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InventarioItemDto(
    @SerialName("articulo_id") val articuloId: String,
    @SerialName("codigo_barras") val codigoBarras: String? = null,
    val sku: String,
    val nombre: String,
    val descripcion: String? = null,
    val categoria: String? = null,
    @SerialName("unidad_medida") val unidadMedida: String,
    @SerialName("precio_venta") val precioVenta: String,
    val costo: String? = null,
    val cantidad: String,
    val ubicacion: String? = null,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class InventarioListResponseDto(
    val items: List<InventarioItemDto>,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    val total: Int,
)

@Serializable
data class ArticuloEdicionRequestDto(
    @SerialName("sucursal_id") val sucursalId: String,
    @SerialName("usuario_id") val usuarioId: String,
    val nombre: String,
    val descripcion: String? = null,
    val categoria: String? = null,
    @SerialName("unidad_medida") val unidadMedida: String,
    @SerialName("precio_venta") val precioVenta: String,
    val costo: String? = null,
    val cantidad: String,
    val ubicacion: String? = null,
)

@Serializable
data class AjusteInventarioDto(
    val articulo: ArticuloDto,
    val inventario: InventarioDto,
    val movimiento: MovimientoDto? = null,
)

@Serializable
data class ValoresDto(
    val valores: List<String>,
)
