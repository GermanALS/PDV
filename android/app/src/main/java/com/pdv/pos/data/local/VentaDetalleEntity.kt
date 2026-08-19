package com.pdv.pos.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "venta_detalle")
data class VentaDetalleEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val ventaId: String,
    val articuloId: String,
    val cantidad: BigDecimal,
    val precioUnitario: BigDecimal,
    val subtotal: BigDecimal,
    val updatedAt: Long,
    val isSynced: Boolean,
    val deletedAt: Long?,
)
