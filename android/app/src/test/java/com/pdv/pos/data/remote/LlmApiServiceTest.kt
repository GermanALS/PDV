package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.ChatCompletionRequestDto
import com.pdv.pos.data.remote.dto.ChatMessageDto
import com.pdv.pos.ia.LlmProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.IOException
import kotlin.test.assertFailsWith

// Prueba el cliente HTTP real (PLAN.md Parte 14, sub-paso 1) contra un
// MockWebServer en vez de los tres proveedores reales: el mismo LlmApiService
// atiende a los tres via url/modelo dinamicos, asi que un servidor de prueba
// alcanza para probar la mecanica HTTP (serializacion, header Authorization,
// manejo de errores) sin depender de las APIs externas reales.
class LlmApiServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var service: LlmApiService

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        service = LlmApiService(client = OkHttpClient(), json = Json { ignoreUnknownKeys = true })
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @ParameterizedTest
    @EnumSource(LlmProvider::class)
    fun `chatCompletions envia el modelo de cada proveedor y parsea la respuesta`(provider: LlmProvider) = runTest {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"role":"assistant","content":"4"}}]}"""))

        val response = service.chatCompletions(
            url = server.url("/chat/completions").toString(),
            apiKey = "token-de-prueba",
            request = ChatCompletionRequestDto(
                model = provider.modeloPorDefecto,
                messages = listOf(ChatMessageDto(role = "user", content = "2+2, responde unicamente el numero")),
            ),
        )

        assertEquals("4", response.choices.first().message.content)
        val recorded = server.takeRequest()
        assertEquals("Bearer token-de-prueba", recorded.getHeader("Authorization"))
        assertEquals(provider.modeloPorDefecto, Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject["model"]?.jsonPrimitive?.content)
    }

    @Test
    fun `chatCompletions lanza LlmHttpException con el status code en un token invalido`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"invalid token"}"""))

        val exception = assertFailsWith<LlmHttpException> {
            service.chatCompletions(
                url = server.url("/chat/completions").toString(),
                apiKey = "token-invalido",
                request = ChatCompletionRequestDto(model = "modelo", messages = emptyList()),
            )
        }

        assertEquals(401, exception.statusCode)
    }

    @Test
    fun `chatCompletions lanza LlmHttpException cuando el proveedor devuelve un error de servidor`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val exception = assertFailsWith<LlmHttpException> {
            service.chatCompletions(
                url = server.url("/chat/completions").toString(),
                apiKey = "token-de-prueba",
                request = ChatCompletionRequestDto(model = "modelo", messages = emptyList()),
            )
        }

        assertEquals(500, exception.statusCode)
    }

    @Test
    fun `chatCompletions lanza IOException cuando no hay conexion con el proveedor`() = runTest {
        server.shutdown()

        assertFailsWith<IOException> {
            service.chatCompletions(
                url = server.url("/chat/completions").toString(),
                apiKey = "token-de-prueba",
                request = ChatCompletionRequestDto(model = "modelo", messages = emptyList()),
            )
        }
    }
}
