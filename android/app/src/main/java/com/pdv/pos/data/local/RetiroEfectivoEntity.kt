package com.pdv.pos.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "retiros_efectivo")
data class RetiroEfectivoEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val sucursalId: String,
    val usuarioId: String,
    val monto: BigDecimal,
    val motivo: String?,
    val fecha: Long,
    val updatedAt: Long,
    val isSynced: Boolean,
    val deletedAt: Long?,
    // Codigo HTTP con que el backend rechazo la fila (0 = desconocido, filas
    // descartadas antes de la Parte 36); null = no rechazada (PLAN.md Parte 36).
    val syncRechazo: Int? = null,
)
