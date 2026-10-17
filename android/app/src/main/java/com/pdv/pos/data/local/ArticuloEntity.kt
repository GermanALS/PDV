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

// Compara solo los atributos de catalogo editables (no ids ni tracking). Los
// BigDecimal se comparan con compareTo: 19.0 y 19.00 son el mismo precio.
fun ArticuloEntity.catalogoDifiere(otro: ArticuloEntity): Boolean =
    codigoBarras != otro.codigoBarras ||
        nombre != otro.nombre ||
        descripcion != otro.descripcion ||
        categoria != otro.categoria ||
        unidadMedida != otro.unidadMedida ||
        precioVenta.compareTo(otro.precioVenta) != 0 ||
        (costo == null) != (otro.costo == null) ||
        (costo != null && otro.costo != null && costo.compareTo(otro.costo) != 0)
