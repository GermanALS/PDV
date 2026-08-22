package com.pdv.pos.ia

import com.pdv.pos.data.remote.ApiResult
import com.pdv.pos.data.remote.LlmApiService
import com.pdv.pos.data.remote.LlmHttpException
import com.pdv.pos.data.remote.dto.ChatCompletionChoiceDto
import com.pdv.pos.data.remote.dto.ChatCompletionRequestDto
import com.pdv.pos.data.remote.dto.ChatCompletionResponseDto
import com.pdv.pos.data.remote.dto.ChatMessageDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.IOException

class LlmClientTest {

    @Test
    fun `chat devuelve el contenido de la respuesta como exito`() = runTest {
        val api = mockk<LlmApiService>()
        coEvery { api.chatCompletions(any(), any(), any()) } returns ChatCompletionResponseDto(
            choices = listOf(ChatCompletionChoiceDto(ChatMessageDto(role = "assistant", content = "4"))),
        )
        val client = LlmClient(api)

        val result = client.chat(LlmProvider.DEEP_SEEK, "token", "deepseek-chat", listOf(ChatMessageDto("user", "2+2")))

        assertEquals(ApiResult.Success("4"), result)
    }

    @Test
    fun `chat devuelve error si el proveedor no incluye respuestas`() = runTest {
        val api = mockk<LlmApiService>()
        coEvery { api.chatCompletions(any(), any(), any()) } returns ChatCompletionResponseDto(choices = emptyList())
        val client = LlmClient(api)

        val result = client.chat(LlmProvider.DEEP_SEEK, "token", "deepseek-chat", listOf(ChatMessageDto("user", "2+2")))

        assertEquals(ApiResult.Error("El proveedor no devolvio ninguna respuesta"), result)
    }

    @Test
    fun `chat traduce un 401 a mensaje de token invalido`() = runTest {
        val api = mockk<LlmApiService>()
        coEvery { api.chatCompletions(any(), any(), any()) } throws LlmHttpException(401)
        val client = LlmClient(api)

        val result = client.chat(LlmProvider.DEEP_SEEK, "token-malo", "deepseek-chat", listOf(ChatMessageDto("user", "2+2")))

        assertEquals(ApiResult.Error("Token invalido o sin permiso para el proveedor seleccionado"), result)
    }

    @Test
    fun `chat traduce un error de servidor a mensaje generico con el status code`() = runTest {
        val api = mockk<LlmApiService>()
        coEvery { api.chatCompletions(any(), any(), any()) } throws LlmHttpException(500)
        val client = LlmClient(api)

        val result = client.chat(LlmProvider.DEEP_SEEK, "token", "deepseek-chat", listOf(ChatMessageDto("user", "2+2")))

        assertEquals(ApiResult.Error("Error del proveedor (HTTP 500)"), result)
    }

    @Test
    fun `chat traduce una falla de conexion a mensaje de sin conexion`() = runTest {
        val api = mockk<LlmApiService>()
        coEvery { api.chatCompletions(any(), any(), any()) } throws IOException("sin red")
        val client = LlmClient(api)

        val result = client.chat(LlmProvider.DEEP_SEEK, "token", "deepseek-chat", listOf(ChatMessageDto("user", "2+2")))

        assertEquals(ApiResult.Error("Sin conexion con el proveedor de IA"), result)
    }

    @ParameterizedTest
    @EnumSource(LlmProvider::class)
    fun `probarConectividad usa la url y el modelo por defecto de cada proveedor`(provider: LlmProvider) = runTest {
        val api = mockk<LlmApiService>()
        coEvery { api.chatCompletions(any(), any(), any()) } returns ChatCompletionResponseDto(
            choices = listOf(ChatCompletionChoiceDto(ChatMessageDto(role = "assistant", content = "4"))),
        )
        val client = LlmClient(api)

        val result = client.probarConectividad(provider, "token")

        assertEquals(ApiResult.Success("4"), result)
        coVerify {
            api.chatCompletions(
                url = provider.chatCompletionsUrl,
                apiKey = "token",
                request = ChatCompletionRequestDto(
                    model = provider.modeloPorDefecto,
                    messages = listOf(ChatMessageDto(role = "user", content = "2+2, responde unicamente el numero")),
                ),
            )
        }
    }
}
