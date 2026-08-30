package com.pdv.pos.ia

import com.pdv.pos.data.remote.ApiResult
import com.pdv.pos.data.remote.LlmApiService
import com.pdv.pos.data.remote.LlmHttpException
import com.pdv.pos.data.remote.dto.ChatCompletionChoiceDto
import com.pdv.pos.data.remote.dto.ChatCompletionRequestDto
import com.pdv.pos.data.remote.dto.ChatCompletionResponseDto
import com.pdv.pos.data.remote.dto.ChatMessageDto
import com.pdv.pos.data.remote.dto.ResponseFormatDto
import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.IOException

class LlmClientTest {

    private val json = Json { ignoreUnknownKeys = true }

    // LlmClient loguea el contenido crudo cuando no parsea (diagnostico de
    // la Parte 20); android.util.Log no esta disponible en tests JVM.
    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `chat devuelve el contenido de la respuesta como exito`() = runTest {
        val api = mockk<LlmApiService>()
        coEvery { api.chatCompletions(any(), any(), any()) } returns ChatCompletionResponseDto(
            choices = listOf(ChatCompletionChoiceDto(ChatMessageDto(role = "assistant", content = "4"))),
        )
        val client = LlmClient(api, json)

        val result = client.chat(LlmProvider.DEEP_SEEK, "token", "deepseek-chat", listOf(ChatMessageDto("user", "2+2")))

        assertEquals(ApiResult.Success("4"), result)
    }

    @Test
    fun `chat devuelve error si el proveedor no incluye respuestas`() = runTest {
        val api = mockk<LlmApiService>()
        coEvery { api.chatCompletions(any(), any(), any()) } returns ChatCompletionResponseDto(choices = emptyList())
        val client = LlmClient(api, json)

        val result = client.chat(LlmProvider.DEEP_SEEK, "token", "deepseek-chat", listOf(ChatMessageDto("user", "2+2")))

        assertEquals(ApiResult.Error("El proveedor no devolvio ninguna respuesta"), result)
    }

    @Test
    fun `chat traduce un 401 a mensaje de token invalido`() = runTest {
        val api = mockk<LlmApiService>()
        coEvery { api.chatCompletions(any(), any(), any()) } throws LlmHttpException(401)
        val client = LlmClient(api, json)

        val result = client.chat(LlmProvider.DEEP_SEEK, "token-malo", "deepseek-chat", listOf(ChatMessageDto("user", "2+2")))

        assertEquals(ApiResult.Error("Token invalido o sin permiso para el proveedor seleccionado"), result)
    }

    @Test
    fun `chat traduce un error de servidor a mensaje generico con el status code`() = runTest {
        val api = mockk<LlmApiService>()
        coEvery { api.chatCompletions(any(), any(), any()) } throws LlmHttpException(500)
        val client = LlmClient(api, json)

        val result = client.chat(LlmProvider.DEEP_SEEK, "token", "deepseek-chat", listOf(ChatMessageDto("user", "2+2")))

        assertEquals(ApiResult.Error("Error del proveedor (HTTP 500)"), result)
    }

    @Test
    fun `chat traduce una falla de conexion a mensaje de sin conexion`() = runTest {
        val api = mockk<LlmApiService>()
        coEvery { api.chatCompletions(any(), any(), any()) } throws IOException("sin red")
        val client = LlmClient(api, json)

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
        val client = LlmClient(api, json)

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

    // PLAN.md Parte 15, sub-paso 2: un ejemplo por cada uno de los 3 tipos
    // de accion que la IA puede proponer, tal como los devolveria el
    // proveedor en el campo "content" bajo response_format=json_object.
    @Test
    fun `chatEstructurado decodifica una alta a inventario con ajuste de costo`() = runTest {
        val contenido = """
            {
              "respuesta_usuario": "Doy de alta 10 unidades de tornillos con costo 5.50",
              "acciones": [
                {
                  "modulo": "entrada",
                  "tipo": "alta_articulo",
                  "parametros": {"sku": "TOR-001", "nombre": "Tornillo 1/2", "cantidad": "10", "costo": "5.50"}
                }
              ]
            }
        """.trimIndent()
        val api = mockk<LlmApiService>()
        coEvery { api.chatCompletions(any(), any(), any()) } returns ChatCompletionResponseDto(
            choices = listOf(ChatCompletionChoiceDto(ChatMessageDto(role = "assistant", content = contenido))),
        )
        val client = LlmClient(api, json)

        val resultado = client.chatEstructurado(
            LlmProvider.DEEP_SEEK, "token", "deepseek-chat", listOf(ChatMessageDto("user", "da de alta 10 tornillos a 5.50")),
        )

        val respuesta = (resultado as ApiResult.Success).data
        assertEquals(1, respuesta.acciones.size)
        assertEquals("entrada", respuesta.acciones[0].modulo)
        assertEquals("alta_articulo", respuesta.acciones[0].tipo)
        assertEquals("5.50", respuesta.acciones[0].parametros["costo"]?.jsonPrimitive?.content)
    }

    @Test
    fun `chatEstructurado decodifica un corte parcial con retiro`() = runTest {
        val contenido = """
            {
              "respuesta_usuario": "Registro el corte parcial con un retiro de 500",
              "acciones": [
                {"modulo": "caja", "tipo": "corte_parcial", "parametros": {}},
                {"modulo": "caja", "tipo": "retiro_efectivo", "parametros": {"monto": "500", "motivo": "deposito"}}
              ]
            }
        """.trimIndent()
        val api = mockk<LlmApiService>()
        coEvery { api.chatCompletions(any(), any(), any()) } returns ChatCompletionResponseDto(
            choices = listOf(ChatCompletionChoiceDto(ChatMessageDto(role = "assistant", content = contenido))),
        )
        val client = LlmClient(api, json)

        val resultado = client.chatEstructurado(
            LlmProvider.OPEN_AI, "token", "gpt-4o-mini", listOf(ChatMessageDto("user", "corte parcial con retiro de 500")),
        )

        val respuesta = (resultado as ApiResult.Success).data
        assertEquals(2, respuesta.acciones.size)
        assertEquals("corte_parcial", respuesta.acciones[0].tipo)
        assertEquals("retiro_efectivo", respuesta.acciones[1].tipo)
        assertEquals("500", respuesta.acciones[1].parametros["monto"]?.jsonPrimitive?.content)
    }

    @Test
    fun `chatEstructurado decodifica una devolucion`() = runTest {
        val contenido = """
            {
              "respuesta_usuario": "Registro la devolucion del articulo REF-001",
              "acciones": [
                {
                  "modulo": "devoluciones",
                  "tipo": "registrar_devolucion",
                  "parametros": {"articuloId": "art-1", "cantidad": "2", "motivo": "producto danado"}
                }
              ]
            }
        """.trimIndent()
        val api = mockk<LlmApiService>()
        coEvery { api.chatCompletions(any(), any(), any()) } returns ChatCompletionResponseDto(
            choices = listOf(ChatCompletionChoiceDto(ChatMessageDto(role = "assistant", content = contenido))),
        )
        val client = LlmClient(api, json)

        val resultado = client.chatEstructurado(
            LlmProvider.OPEN_ROUTER, "token", "openai/gpt-4o-mini", listOf(ChatMessageDto("user", "devolucion de REF-001")),
        )

        val respuesta = (resultado as ApiResult.Success).data
        assertEquals("devoluciones", respuesta.acciones[0].modulo)
        assertEquals("2", respuesta.acciones[0].parametros["cantidad"]?.jsonPrimitive?.content)
    }

    @Test
    fun `chatEstructurado usa response_format json_object`() = runTest {
        val api = mockk<LlmApiService>()
        coEvery { api.chatCompletions(any(), any(), any()) } returns ChatCompletionResponseDto(
            choices = listOf(
                ChatCompletionChoiceDto(ChatMessageDto(role = "assistant", content = """{"respuesta_usuario": "ok"}""")),
            ),
        )
        val client = LlmClient(api, json)

        client.chatEstructurado(LlmProvider.DEEP_SEEK, "token", "deepseek-chat", listOf(ChatMessageDto("user", "hola")))

        coVerify {
            api.chatCompletions(
                url = LlmProvider.DEEP_SEEK.chatCompletionsUrl,
                apiKey = "token",
                request = ChatCompletionRequestDto(
                    model = "deepseek-chat",
                    messages = listOf(ChatMessageDto("user", "hola")),
                    responseFormat = ResponseFormatDto(type = "json_object"),
                ),
            )
        }
    }

    @Test
    fun `chatEstructurado devuelve error si el proveedor no respeta el esquema`() = runTest {
        val api = mockk<LlmApiService>()
        coEvery { api.chatCompletions(any(), any(), any()) } returns ChatCompletionResponseDto(
            choices = listOf(ChatCompletionChoiceDto(ChatMessageDto(role = "assistant", content = "esto no es json"))),
        )
        val client = LlmClient(api, json)

        val resultado = client.chatEstructurado(
            LlmProvider.DEEP_SEEK, "token", "deepseek-chat", listOf(ChatMessageDto("user", "hola")),
        )

        assertTrue(resultado is ApiResult.Error)
        assertEquals("La IA devolvio una respuesta con formato invalido", (resultado as ApiResult.Error).message)
        // El contenido crudo se loguea para poder diagnosticar el caso
        // (PLAN.md Parte 20): sin esto un "formato invalido" intermitente no
        // se puede reproducir.
        verify { Log.w("LlmClient", match<String> { it.contains("esto no es json") }) }
    }

    // PLAN.md Parte 20 (ronda 2 de diagnostico): un "content" vacio con
    // response_format json_object no es "formato invalido" - es truncado por
    // tokens / filtro. Se distingue con su propio mensaje y se loguea
    // finish_reason + usage.
    @Test
    fun `chatEstructurado trata un contenido vacio como respuesta ausente y loguea finish_reason`() = runTest {
        val api = mockk<LlmApiService>()
        coEvery { api.chatCompletions(any(), any(), any()) } returns ChatCompletionResponseDto(
            choices = listOf(
                ChatCompletionChoiceDto(
                    message = ChatMessageDto(role = "assistant", content = ""),
                    finishReason = "length",
                ),
            ),
        )
        val client = LlmClient(api, json)

        val resultado = client.chatEstructurado(
            LlmProvider.DEEP_SEEK, "token", "deepseek-chat", listOf(ChatMessageDto("user", "hola")),
        )

        assertTrue(resultado is ApiResult.Error)
        assertEquals("El proveedor no devolvio ninguna respuesta", (resultado as ApiResult.Error).message)
        verify { Log.w("LlmClient", match<String> { it.contains("finish_reason=length") }) }
    }
}
