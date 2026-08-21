package com.pdv.pos.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "devolucion_detalle")
data class DevolucionDetalleEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val devolucionId: String,
    val articuloId: String,
    val cantidad: BigDecimal,
    val motivo: String?,
    val condicion: String?,
    val updatedAt: Long,
    val isSynced: Boolean,
    val deletedAt: Long?,
)
