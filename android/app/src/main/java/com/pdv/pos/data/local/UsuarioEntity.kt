package com.pdv.pos.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// passwordHash reservado para la Parte 13 (login real contra
// UsuarioRepository); nulo mientras el login siga siendo ficticio
// (PLAN.md Parte 4). Campos de tracking de sync: PLAN.md Parte 3.
@Entity(
    tableName = "usuarios",
    indices = [Index(value = ["username"], unique = true)],
)
data class UsuarioEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val username: String,
    val nombreCompleto: String,
    val passwordHash: String?,
    val rol: String,
    val activo: Boolean,
    val updatedAt: Long,
    val isSynced: Boolean,
    val deletedAt: Long?,
)
