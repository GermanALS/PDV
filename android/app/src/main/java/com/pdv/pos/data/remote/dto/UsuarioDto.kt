package com.pdv.pos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UsuarioDto(
    val id: String,
    @SerialName("local_id") val localId: String? = null,
    val username: String,
    @SerialName("nombre_completo") val nombreCompleto: String,
    @SerialName("rol_id") val rolId: String,
    val activo: Boolean,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("is_synced") val isSynced: Boolean = true,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class UsuarioListResponseDto(
    val items: List<UsuarioDto>,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    val total: Int,
)

@Serializable
data class UsuarioCreateRequestDto(
    @SerialName("local_id") val localId: String? = null,
    val username: String,
    @SerialName("nombre_completo") val nombreCompleto: String,
    @SerialName("rol_id") val rolId: String,
    val activo: Boolean = true,
    // Ya hasheado en el dispositivo (PLAN.md Parte 13, Decision 2); null =
    // usuario creado sin contrasena todavia.
    @SerialName("password_hash") val passwordHash: String? = null,
)

@Serializable
data class UsuarioUpdateRequestDto(
    val username: String,
    @SerialName("nombre_completo") val nombreCompleto: String,
    @SerialName("rol_id") val rolId: String,
    val activo: Boolean,
    // null = no cambiar la contrasena existente (semantica PATCH).
    @SerialName("password_hash") val passwordHash: String? = null,
)
