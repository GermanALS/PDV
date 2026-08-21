package com.pdv.pos.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devoluciones")
data class DevolucionEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val sucursalId: String,
    val usuarioId: String,
    val ventaId: String?,
    val folio: String,
    val fecha: Long,
    val estado: String,
    val updatedAt: Long,
    val isSynced: Boolean,
    val deletedAt: Long?,
)
