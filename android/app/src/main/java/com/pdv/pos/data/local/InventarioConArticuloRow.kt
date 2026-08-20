package com.pdv.pos.data.local

import java.math.BigDecimal

// Fila cruda del join articulos+inventario (InventarioDao) - Room la mapea
// por nombre de columna desde el SELECT con alias en camelCase.
data class InventarioConArticuloRow(
    val articuloLocalId: String,
    val codigoBarras: String?,
    val sku: String,
    val nombre: String,
    val descripcion: String?,
    val categoria: String?,
    val unidadMedida: String,
    val precioVenta: BigDecimal,
    val costo: BigDecimal?,
    val activo: Boolean,
    val cantidad: BigDecimal,
    val ubicacion: String?,
)
