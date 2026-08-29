package com.pdv.pos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// valor_local/valor_remoto/valor_resuelto son objetos JSON libres en el
// contrato (docs/api-contract.md 3); se transportan como JsonElement y el
// repositorio los aplana a String para el modelo de dominio.
@Serializable
data class SyncConflictDto(
    val id: String,
    val entidad: String,
    @SerialName("entidad_local_id") val entidadLocalId: String,
    @SerialName("sucursal_id") val sucursalId: String? = null,
    @SerialName("valor_local") val valorLocal: JsonElement,
    @SerialName("valor_remoto") val valorRemoto: JsonElement,
    @SerialName("valor_resuelto") val valorResuelto: JsonElement,
    @SerialName("politica_aplicada") val politicaAplicada: String,
    @SerialName("resuelto_automaticamente") val resueltoAutomaticamente: Boolean,
    @SerialName("fecha_deteccion") val fechaDeteccion: String,
)

@Serializable
data class SyncConflictListResponseDto(
    val items: List<SyncConflictDto>,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    val total: Int,
)

@Serializable
data class SyncConflictCreateRequestDto(
    val id: String,
    val entidad: String,
    @SerialName("entidad_local_id") val entidadLocalId: String,
    @SerialName("sucursal_id") val sucursalId: String? = null,
    @SerialName("valor_local") val valorLocal: JsonElement,
    @SerialName("valor_remoto") val valorRemoto: JsonElement,
    @SerialName("valor_resuelto") val valorResuelto: JsonElement,
    @SerialName("politica_aplicada") val politicaAplicada: String,
    @SerialName("resuelto_automaticamente") val resueltoAutomaticamente: Boolean,
    @SerialName("fecha_deteccion") val fechaDeteccion: String,
)
