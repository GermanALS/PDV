package com.pdv.pos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RolDto(
    val id: String,
    @SerialName("local_id") val localId: String? = null,
    val nombre: String,
    @SerialName("modulos_permitidos") val modulosPermitidos: List<String>,
    @SerialName("es_sistema") val esSistema: Boolean,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("is_synced") val isSynced: Boolean = true,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class RolListResponseDto(
    val items: List<RolDto>,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    val total: Int,
)

@Serializable
data class RolCreateRequestDto(
    @SerialName("local_id") val localId: String? = null,
    val nombre: String,
    @SerialName("modulos_permitidos") val modulosPermitidos: List<String>,
)

@Serializable
data class RolUpdateRequestDto(
    val nombre: String,
    @SerialName("modulos_permitidos") val modulosPermitidos: List<String>,
)
