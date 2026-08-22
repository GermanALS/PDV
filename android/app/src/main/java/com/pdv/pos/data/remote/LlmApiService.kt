package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.ChatCompletionRequestDto
import com.pdv.pos.data.remote.dto.ChatCompletionResponseDto
import com.pdv.pos.di.SinLoggingHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

// Lanzada en respuestas HTTP no exitosas - a diferencia del resto del
// proyecto (Retrofit + HttpException), este cliente usa OkHttp puro porque
// la URL de destino es dinamica por proveedor (PLAN.md Parte 14): Retrofit
// exige una baseUrl fija en tiempo de configuracion.
class LlmHttpException(val statusCode: Int) : Exception("HTTP $statusCode")

// Sin @Singleton: el estado (client, json) ya es singleton via DI; esta
// clase en si no guarda estado propio.
class LlmApiService @Inject constructor(
    @SinLoggingHttpClient private val client: OkHttpClient,
    private val json: Json,
) {
    suspend fun chatCompletions(
        url: String,
        apiKey: String,
        request: ChatCompletionRequestDto,
    ): ChatCompletionResponseDto {
        val body = json.encodeToString(ChatCompletionRequestDto.serializer(), request)
            .toRequestBody("application/json".toMediaType())
        val httpRequest = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()
        return withContext(Dispatchers.IO) {
            client.newCall(httpRequest).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw LlmHttpException(response.code)
                json.decodeFromString(ChatCompletionResponseDto.serializer(), responseBody)
            }
        }
    }
}
