package com.pdv.pos.ia

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Esquema unico de salida estructurada de la IA (PLAN.md Parte 15,
// sub-paso 2): "modulo"/"tipo" identifican que caso de uso real ejecutar
// (sub-paso 3), "parametros" queda sin tipar aca a proposito - cada tipo de
// accion tiene una forma distinta de parametros, y mapearlos a los modelos
// de dominio reales (Entrada, EdicionArticulo, CorteCaja/RetiroEfectivo,
// Devolucion) es responsabilidad del validador/ejecutor del sub-paso 3, no
// de este esquema de transporte.
@Serializable
data class AccionIaDto(
    val modulo: String,
    val tipo: String,
    val parametros: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class RespuestaIaDto(
    @SerialName("respuesta_usuario") val respuestaUsuario: String,
    val acciones: List<AccionIaDto> = emptyList(),
)
