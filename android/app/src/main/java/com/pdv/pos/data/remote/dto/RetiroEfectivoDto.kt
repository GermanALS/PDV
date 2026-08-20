package com.pdv.pos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RetiroEfectivoCreateRequestDto(
    @SerialName("local_id") val localId: String? = null,
    @SerialName("sucursal_id") val sucursalId: String,
    @SerialName("usuario_id") val usuarioId: String,
    val monto: String,
    val motivo: String? = null,
    val fecha: String,
)

@Serializable
data class RetiroEfectivoDto(
    val id: String,
    @SerialName("local_id") val localId: String? = null,
    @SerialName("sucursal_id") val sucursalId: String,
    @SerialName("usuario_id") val usuarioId: String,
    val monto: String,
    val motivo: String? = null,
    val fecha: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("is_synced") val isSynced: Boolean = true,
    @SerialName("deleted_at") val deletedAt: String? = null,
)
