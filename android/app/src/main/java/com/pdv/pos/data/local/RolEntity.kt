package com.pdv.pos.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Campos de tracking de sync: PLAN.md Parte 3. esSistema protege a
// Administrador/Encargado de turno (seed del backend) de edicion/eliminacion.
@Entity(
    tableName = "roles",
    indices = [Index(value = ["nombre"], unique = true)],
)
data class RolEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val nombre: String,
    val modulosPermitidos: List<String>,
    val esSistema: Boolean,
    val updatedAt: Long,
    val isSynced: Boolean,
    val deletedAt: Long?,
)
