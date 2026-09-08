package com.pdv.pos.data.remote

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.config.ConfiguracionPreferences
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DynamicHostInterceptorTest {

    private val baseRequest = Request.Builder().url("http://localhost:8000/api/v1/health").build()

    private fun preferences(tempDir: File): ConfiguracionPreferences {
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { File(tempDir, "test.preferences_pb") })
        return ConfiguracionPreferences(dataStore)
    }

    private fun chainCapturing(enviado: CapturingSlot<Request>): Interceptor.Chain {
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns baseRequest
        every { chain.proceed(capture(enviado)) } returns Response.Builder()
            .request(baseRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("".toResponseBody())
            .build()
        return chain
    }

    @Test
    fun `rewrites host and port to the saved connection values`(@TempDir tempDir: File) {
        val preferences = preferences(tempDir)
        runBlocking { preferences.setConexion(ip = "192.168.1.42", puerto = "9100") }
        val enviado = slot<Request>()

        DynamicHostInterceptor(preferences).intercept(chainCapturing(enviado))

        assertEquals("192.168.1.42", enviado.captured.url.host)
        assertEquals(9100, enviado.captured.url.port)
        assertEquals("/api/v1/health", enviado.captured.url.encodedPath)
    }

    @Test
    fun `leaves the request untouched when no connection is saved`(@TempDir tempDir: File) {
        val preferences = preferences(tempDir)
        val enviado = slot<Request>()

        DynamicHostInterceptor(preferences).intercept(chainCapturing(enviado))

        assertEquals("localhost", enviado.captured.url.host)
        assertEquals(8000, enviado.captured.url.port)
    }

    @Test
    fun `refreshes the cached config when the connection changes after construction`(@TempDir tempDir: File) {
        val preferences = preferences(tempDir)
        val interceptor = DynamicHostInterceptor(preferences)

        runBlocking { preferences.setConexion(ip = "10.0.0.5", puerto = "7000") }

        // El colector interno corre en Dispatchers.Default; espera a que observe el cambio.
        val actualizado = runBlocking {
            withTimeoutOrNull(2_000) {
                var capturado: Request? = null
                while (capturado?.url?.host != "10.0.0.5") {
                    val enviado = slot<Request>()
                    interceptor.intercept(chainCapturing(enviado))
                    capturado = enviado.captured
                    if (capturado.url.host != "10.0.0.5") delay(20)
                }
                capturado
            }
        }

        assertEquals("10.0.0.5", actualizado?.url?.host)
        assertEquals(7000, actualizado?.url?.port)
    }
}
