package com.pdv.pos.ia

import com.pdv.pos.data.remote.ApiResult
import com.pdv.pos.data.remote.LlmApiService
import com.pdv.pos.data.remote.LlmHttpException
import com.pdv.pos.data.remote.dto.ChatCompletionRequestDto
import com.pdv.pos.data.remote.dto.ChatMessageDto
import com.pdv.pos.data.remote.dto.ResponseFormatDto
import android.util.Log
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject

// Constante compartida con ChatViewModel (PLAN.md Parte 16, sub-paso 3): al
// recibir este mensaje exacto, el widget de chat interpreta que no hay
// conexion a internet (deteccion reactiva, no proactiva) y muestra el FAQ
// empaquetado en vez de un error generico.
const val MENSAJE_SIN_CONEXION_IA = "Sin conexion con el proveedor de IA"

// Cliente unico para los tres proveedores (PLAN.md Parte 14): DeepSeek,
// OpenAI y OpenRouter exponen el mismo formato "chat completions", asi que
// variar LlmProvider alcanza sin una implementacion por proveedor.
class LlmClient @Inject constructor(
    private val api: LlmApiService,
    private val json: Json,
) {
    suspend fun chat(
        provider: LlmProvider,
        apiKey: String,
        modelo: String,
        mensajes: List<ChatMessageDto>,
    ): ApiResult<String> = try {
        val response = api.chatCompletions(
            url = provider.chatCompletionsUrl,
            apiKey = apiKey,
            request = ChatCompletionRequestDto(model = modelo, messages = mensajes),
        )
        val contenido = response.choices.firstOrNull()?.message?.content
        if (contenido != null) {
            ApiResult.Success(contenido)
        } else {
            ApiResult.Error("El proveedor no devolvio ninguna respuesta")
        }
    } catch (e: LlmHttpException) {
        ApiResult.Error(mensajeDeErrorHttp(e))
    } catch (e: IOException) {
        ApiResult.Error(MENSAJE_SIN_CONEXION_IA)
    }

    // Prueba de conectividad "2+2" (PLAN.md Parte 14, sub-paso 1).
    suspend fun probarConectividad(
        provider: LlmProvider,
        apiKey: String,
        modelo: String = provider.modeloPorDefecto,
    ): ApiResult<String> = chat(
        provider = provider,
        apiKey = apiKey,
        modelo = modelo,
        mensajes = listOf(ChatMessageDto(role = "user", content = "2+2, responde unicamente el numero")),
    )

    // Salida estructurada (PLAN.md Parte 15, sub-paso 2): a diferencia de
    // chat(), aca el contenido de la respuesta es JSON obligatorio segun el
    // esquema RespuestaIaDto - "json_object" (soportado por los tres
    // proveedores) mas la instruccion del formato exacto en el prompt de
    // sistema (responsabilidad del llamador, no de este cliente) son lo que
    // garantiza que el LLM lo respete.
    suspend fun chatEstructurado(
        provider: LlmProvider,
        apiKey: String,
        modelo: String,
        mensajes: List<ChatMessageDto>,
    ): ApiResult<RespuestaIaDto> = try {
        val response = api.chatCompletions(
            url = provider.chatCompletionsUrl,
            apiKey = apiKey,
            request = ChatCompletionRequestDto(
                model = modelo,
                messages = mensajes,
                responseFormat = ResponseFormatDto(type = "json_object"),
            ),
        )
        val choice = response.choices.firstOrNull()
        val contenido = choice?.message?.content
        if (contenido.isNullOrBlank()) {
            // Diagnostico (PLAN.md Parte 20): un contenido vacio con
            // response_format json_object suele ser truncado por tokens
            // ("length"), filtro de contenido, o el modelo escribiendo en
            // otro campo (razonamiento). finish_reason y usage lo distinguen.
            Log.w(
                "LlmClient",
                "El proveedor devolvio contenido vacio. finish_reason=${choice?.finishReason} " +
                    "prompt_tokens=${response.usage?.promptTokens} " +
                    "completion_tokens=${response.usage?.completionTokens}",
            )
            ApiResult.Error("El proveedor no devolvio ninguna respuesta")
        } else {
            try {
                ApiResult.Success(json.decodeFromString(RespuestaIaDto.serializer(), contenido))
            } catch (e: SerializationException) {
                // El texto crudo del modelo no se guarda en ningun lado, asi
                // que un "formato invalido" intermitente es imposible de
                // reproducir. Se loguea solo el contenido de la respuesta
                // (truncado) - el token va unicamente en el header del
                // request, nunca aca.
                Log.w(
                    "LlmClient",
                    "Respuesta no parseable a RespuestaIaDto (${e.message}). " +
                        "finish_reason=${choice.finishReason} " +
                        "completion_tokens=${response.usage?.completionTokens} " +
                        "Contenido: ${contenido.take(1500)}",
                )
                ApiResult.Error("La IA devolvio una respuesta con formato invalido")
            }
        }
    } catch (e: LlmHttpException) {
        ApiResult.Error(mensajeDeErrorHttp(e))
    } catch (e: IOException) {
        ApiResult.Error(MENSAJE_SIN_CONEXION_IA)
    }
}

private fun mensajeDeErrorHttp(e: LlmHttpException): String = when (e.statusCode) {
    401, 403 -> "Token invalido o sin permiso para el proveedor seleccionado"
    else -> "Error del proveedor (HTTP ${e.statusCode})"
}
