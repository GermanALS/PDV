package com.pdv.pos.data.remote

import com.pdv.pos.config.ConfiguracionPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

// Reescribe host/puerto de cada request con la IP/Puerto guardados en
// ConfiguracionPreferences, para que cambiar la conexion desde la pantalla
// de Configuracion tenga efecto sin reiniciar la app ni reconstruir
// Retrofit. Si no hay IP o el puerto no es numerico, deja pasar la request
// tal cual (usa el host del baseUrl: localhost:8000 para adb reverse /
// 10.0.2.2 en emulador). runBlocking es aceptable: corre en el hilo de
// dispatch de OkHttp y DataStore cachea en memoria tras la primera lectura.
@Singleton
class DynamicHostInterceptor @Inject constructor(
    private val preferences: ConfiguracionPreferences,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val config = runBlocking { preferences.deviceConfig.first() }
        val host = config.ip.trim()
        val puerto = config.puerto.trim().toIntOrNull()
        val request = chain.request()
        if (host.isEmpty() || puerto == null) {
            return chain.proceed(request)
        }
        val nuevaUrl = request.url.newBuilder()
            .host(host)
            .port(puerto)
            .build()
        return chain.proceed(request.newBuilder().url(nuevaUrl).build())
    }
}
