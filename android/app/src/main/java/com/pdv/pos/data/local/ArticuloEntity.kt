package com.pdv.pos.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

// Catalogo maestro, global entre sucursales (docs/schema-pos.json). Primera
// escritura real la hace la Parte 8 (entrada de mercancia con articulo
// nuevo); antes solo existia como modelo de dominio con datos estaticos
// (VentaViewModel, Parte 7).
@Entity(tableName = "articulos")
data class ArticuloEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val codigoBarras: String?,
    val sku: String,
    val nombre: String,
    val descripcion: String?,
    val categoria: String?,
    val unidadMedida: String,
    val precioVenta: BigDecimal,
    val costo: BigDecimal?,
    val activo: Boolean,
    val updatedAt: Long,
    val isSynced: Boolean,
    val deletedAt: Long?,
)
