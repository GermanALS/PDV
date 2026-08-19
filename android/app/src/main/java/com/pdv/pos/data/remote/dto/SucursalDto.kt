package com.pdv.pos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SucursalDto(
    val id: String,
    @SerialName("local_id") val localId: String? = null,
    val nombre: String,
    val direccion: String? = null,
    val activa: Boolean,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("is_synced") val isSynced: Boolean = true,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class SucursalListResponseDto(
    val items: List<SucursalDto>,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    val total: Int,
)

@Serializable
data class SucursalCreateRequestDto(
    @SerialName("local_id") val localId: String? = null,
    val nombre: String,
    val direccion: String? = null,
    val activa: Boolean = true,
)
