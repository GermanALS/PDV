package com.pdv.pos.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

// cantidad es un campo real, no una vista derivada de movimientos - se
// actualiza siempre via EventoAditivoCombiner (PLAN.md Parte 6, "Decisiones
// abiertas"), nunca con un UPDATE cantidad = X directo (EntradaDao).
@Entity(
    tableName = "inventario",
    indices = [Index(value = ["sucursalId", "articuloId"], unique = true)],
)
data class InventarioEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val sucursalId: String,
    val articuloId: String,
    val cantidad: BigDecimal,
    val ubicacion: String?,
    val updatedAt: Long,
    val isSynced: Boolean,
    val deletedAt: Long?,
)
