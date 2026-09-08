package com.pdv.pos.di

import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkModuleTest {

    private val passthrough = Interceptor { chain -> chain.proceed(chain.request()) }

    @Test
    fun `release client (sin logging de red) no incluye HttpLoggingInterceptor`() {
        val client = buildOkHttpClient(passthrough, passthrough, includeNetworkLogging = false)

        assertTrue(client.interceptors.none { it is HttpLoggingInterceptor }) {
            "el build de release no debe incluir el interceptor de logging de red (M-8)"
        }
    }

    @Test
    fun `debug client incluye un HttpLoggingInterceptor en nivel BASIC`() {
        val client = buildOkHttpClient(passthrough, passthrough, includeNetworkLogging = true)

        val logging = client.interceptors.filterIsInstance<HttpLoggingInterceptor>()
        assertEquals(1, logging.size)
        assertEquals(HttpLoggingInterceptor.Level.BASIC, logging.single().level)
    }
}
