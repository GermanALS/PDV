package com.pdv.pos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UsuarioDto(
    val id: String,
    @SerialName("local_id") val localId: String? = null,
    val username: String,
    @SerialName("nombre_completo") val nombreCompleto: String,
    val rol: String,
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
    val rol: String,
    val activo: Boolean = true,
)

@Serializable
data class UsuarioUpdateRequestDto(
    val username: String,
    @SerialName("nombre_completo") val nombreCompleto: String,
    val rol: String,
    val activo: Boolean,
)
