package com.pdv.pos.data.remote

import com.pdv.pos.domain.model.EsquemaConexion
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

// Prueba puntual de alcance del backend (PLAN.md Parte 31, boton "Probar
// conexion") contra un MockWebServer: cubre el GET /api/v1/health real, el
// mapeo de estados y el fallo de conexion, sin depender de un backend vivo.
class BackendHealthCheckerTest {

    private lateinit var server: MockWebServer
    private val checker = BackendHealthChecker()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a 200 response is a success carrying the body`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"ok","version":"0.1.0"}"""))

        val resultado = checker.probar(EsquemaConexion.HTTP, server.hostName, server.port.toString())

        assertTrue(resultado is ApiResult.Success)
        assertTrue((resultado as ApiResult.Success).data.contains("ok"))
        assertEquals("/api/v1/health", server.takeRequest().path)
    }

    @Test
    fun `a server error response is mapped to an error result with the status code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val resultado = checker.probar(EsquemaConexion.HTTP, server.hostName, server.port.toString())

        assertTrue(resultado is ApiResult.Error)
        assertTrue((resultado as ApiResult.Error).message.contains("500"))
    }

    @Test
    fun `an unreachable host is mapped to a connection error`() = runTest {
        val puerto = server.port
        server.shutdown()

        val resultado = checker.probar(EsquemaConexion.HTTP, "localhost", puerto.toString())

        assertTrue(resultado is ApiResult.Error)
        assertTrue((resultado as ApiResult.Error).message.startsWith("No se pudo conectar"))
    }

    @Test
    fun `a non numeric port is rejected before any network call`() = runTest {
        val resultado = checker.probar(EsquemaConexion.HTTP, "localhost", "ochomil")

        assertTrue(resultado is ApiResult.Error)
        assertTrue((resultado as ApiResult.Error).message.contains("Puerto invalido"))
    }

    @Test
    fun `a blank host is rejected`() = runTest {
        val resultado = checker.probar(EsquemaConexion.HTTP, "   ", "8000")

        assertTrue(resultado is ApiResult.Error)
        assertEquals("Falta el host", (resultado as ApiResult.Error).message)
    }
}
