package com.pdv.pos.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "ventas")
data class VentaEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val sucursalId: String,
    val usuarioId: String,
    val folio: String,
    val fecha: Long,
    val subtotal: BigDecimal,
    val descuento: BigDecimal,
    val impuestos: BigDecimal,
    val total: BigDecimal,
    val metodoPago: String,
    val estado: String,
    val updatedAt: Long,
    val isSynced: Boolean,
    val deletedAt: Long?,
    // Codigo HTTP con que el backend rechazo la fila (0 = desconocido, filas
    // descartadas antes de la Parte 36); null = no rechazada (PLAN.md Parte 36).
    val syncRechazo: Int? = null,
)
