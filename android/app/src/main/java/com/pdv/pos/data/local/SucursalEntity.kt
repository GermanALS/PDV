package com.pdv.pos.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// Campos de tracking de sync (PLAN.md Parte 3): localId/remoteId/updatedAt/
// isSynced/deletedAt presentes en toda tabla sincronizable.
@Entity(tableName = "sucursales")
data class SucursalEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val nombre: String,
    val direccion: String?,
    val activa: Boolean,
    val updatedAt: Long,
    val isSynced: Boolean,
    val deletedAt: Long?,
)
