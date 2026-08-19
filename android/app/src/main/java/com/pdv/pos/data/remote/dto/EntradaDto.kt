package com.pdv.pos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Los montos/cantidades viajan como String (no numero crudo), misma
// convencion que VentaDto (evita perder precision decimal).
@Serializable
data class ArticuloNuevoRequestDto(
    @SerialName("local_id") val localId: String? = null,
    @SerialName("codigo_barras") val codigoBarras: String? = null,
    val sku: String,
    val nombre: String,
    val descripcion: String? = null,
    val categoria: String? = null,
    @SerialName("unidad_medida") val unidadMedida: String,
    @SerialName("precio_venta") val precioVenta: String,
    val costo: String? = null,
)

@Serializable
data class EntradaCreateRequestDto(
    @SerialName("local_id") val localId: String? = null,
    @SerialName("sucursal_id") val sucursalId: String,
    @SerialName("usuario_id") val usuarioId: String,
    val fecha: String,
    val cantidad: String,
    val ubicacion: String? = null,
    @SerialName("articulo_id") val articuloId: String? = null,
    @SerialName("articulo_nuevo") val articuloNuevo: ArticuloNuevoRequestDto? = null,
)

@Serializable
data class ArticuloDto(
    val id: String,
    @SerialName("local_id") val localId: String? = null,
    @SerialName("codigo_barras") val codigoBarras: String? = null,
    val sku: String,
    val nombre: String,
    val descripcion: String? = null,
    val categoria: String? = null,
    @SerialName("unidad_medida") val unidadMedida: String,
    @SerialName("precio_venta") val precioVenta: String,
    val costo: String? = null,
    val activo: Boolean = true,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("is_synced") val isSynced: Boolean = true,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class InventarioDto(
    val id: String,
    @SerialName("local_id") val localId: String? = null,
    @SerialName("sucursal_id") val sucursalId: String,
    @SerialName("articulo_id") val articuloId: String,
    val cantidad: String,
    val ubicacion: String? = null,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("is_synced") val isSynced: Boolean = true,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class MovimientoDto(
    val id: String,
    @SerialName("local_id") val localId: String? = null,
    @SerialName("sucursal_id") val sucursalId: String,
    @SerialName("articulo_id") val articuloId: String,
    @SerialName("usuario_id") val usuarioId: String,
    val tipo: String,
    val cantidad: String,
    val ubicacion: String? = null,
    @SerialName("referencia_tipo") val referenciaTipo: String? = null,
    @SerialName("referencia_id") val referenciaId: String? = null,
    val fecha: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("is_synced") val isSynced: Boolean = true,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class EntradaDto(
    val movimiento: MovimientoDto,
    val inventario: InventarioDto,
    val articulo: ArticuloDto? = null,
)
