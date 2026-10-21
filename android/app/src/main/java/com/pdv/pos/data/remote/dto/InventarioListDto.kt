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
    @SerialName("usuario_dispositivo") val usuarioDispositivo: String,
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
data class AjusteDeltaRequestDto(
    @SerialName("local_id") val localId: String,
    @SerialName("sucursal_id") val sucursalId: String,
    @SerialName("articulo_id") val articuloId: String,
    @SerialName("usuario_dispositivo") val usuarioDispositivo: String,
    val fecha: String,
    val delta: String,
)

@Serializable
data class AjusteDeltaDto(
    val movimiento: MovimientoDto,
    val inventario: InventarioDto,
)

@Serializable
data class ArticuloCatalogoRequestDto(
    @SerialName("sucursal_id") val sucursalId: String,
    val nombre: String,
    val descripcion: String? = null,
    val categoria: String? = null,
    @SerialName("unidad_medida") val unidadMedida: String,
    @SerialName("precio_venta") val precioVenta: String,
    val costo: String? = null,
    val ubicacion: String? = null,
    @SerialName("editado_en") val editadoEn: String,
)

@Serializable
data class ArticuloCatalogoDto(
    val aplicado: Boolean,
    val articulo: ArticuloDto,
    val inventario: InventarioDto? = null,
)

@Serializable
data class ValoresDto(
    val valores: List<String>,
)
