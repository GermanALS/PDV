package com.pdv.pos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Shape "chat completions" comun a DeepSeek/OpenAI/OpenRouter (PLAN.md
// Parte 14) - las tres APIs son compatibles con el formato de OpenAI.
@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String,
)

// "json_object" es el modo de salida estructurada comun a los tres
// proveedores (PLAN.md Parte 15, sub-paso 2) - a diferencia del modo
// "json_schema" estricto de OpenAI, json_object esta soportado tal cual por
// DeepSeek y por la mayoria de los modelos que enruta OpenRouter, asi que
// mantiene el cliente unico sin ramificar por proveedor.
@Serializable
data class ResponseFormatDto(
    val type: String,
)

@Serializable
data class ChatCompletionRequestDto(
    val model: String,
    val messages: List<ChatMessageDto>,
    @SerialName("response_format") val responseFormat: ResponseFormatDto? = null,
)

@Serializable
data class ChatCompletionChoiceDto(
    val message: ChatMessageDto,
)

@Serializable
data class ChatCompletionResponseDto(
    val choices: List<ChatCompletionChoiceDto>,
)
