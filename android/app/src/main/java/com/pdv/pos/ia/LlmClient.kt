package com.pdv.pos.ia

import com.pdv.pos.data.remote.ApiResult
import com.pdv.pos.data.remote.LlmApiService
import com.pdv.pos.data.remote.LlmHttpException
import com.pdv.pos.data.remote.dto.ChatCompletionRequestDto
import com.pdv.pos.data.remote.dto.ChatMessageDto
import com.pdv.pos.data.remote.dto.ResponseFormatDto
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject

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
        ApiResult.Error("Sin conexion con el proveedor de IA")
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
        val contenido = response.choices.firstOrNull()?.message?.content
        if (contenido == null) {
            ApiResult.Error("El proveedor no devolvio ninguna respuesta")
        } else {
            try {
                ApiResult.Success(json.decodeFromString(RespuestaIaDto.serializer(), contenido))
            } catch (e: SerializationException) {
                ApiResult.Error("La IA devolvio una respuesta con formato invalido")
            }
        }
    } catch (e: LlmHttpException) {
        ApiResult.Error(mensajeDeErrorHttp(e))
    } catch (e: IOException) {
        ApiResult.Error("Sin conexion con el proveedor de IA")
    }
}

private fun mensajeDeErrorHttp(e: LlmHttpException): String = when (e.statusCode) {
    401, 403 -> "Token invalido o sin permiso para el proveedor seleccionado"
    else -> "Error del proveedor (HTTP ${e.statusCode})"
}
