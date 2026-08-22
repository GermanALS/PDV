package com.pdv.pos.ia

import com.pdv.pos.data.remote.ApiResult
import com.pdv.pos.data.remote.LlmApiService
import com.pdv.pos.data.remote.LlmHttpException
import com.pdv.pos.data.remote.dto.ChatCompletionRequestDto
import com.pdv.pos.data.remote.dto.ChatMessageDto
import java.io.IOException
import javax.inject.Inject

// Cliente unico para los tres proveedores (PLAN.md Parte 14): DeepSeek,
// OpenAI y OpenRouter exponen el mismo formato "chat completions", asi que
// variar LlmProvider alcanza sin una implementacion por proveedor.
class LlmClient @Inject constructor(
    private val api: LlmApiService,
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
}

private fun mensajeDeErrorHttp(e: LlmHttpException): String = when (e.statusCode) {
    401, 403 -> "Token invalido o sin permiso para el proveedor seleccionado"
    else -> "Error del proveedor (HTTP ${e.statusCode})"
}
