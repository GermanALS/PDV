package com.pdv.pos.data.remote

import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.config.DeviceConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
// 10.0.2.2 en emulador).
//
// El valor se cachea en un campo volatil: se siembra una sola vez de forma
// bloqueante al construir el interceptor (singleton) y despues lo mantiene
// al dia un colector, en vez de reejecutar el pipeline del Flow con
// runBlocking en el hilo de dispatch de OkHttp por cada request
// (PLAN.md Parte 29, B-5).
@Singleton
class DynamicHostInterceptor @Inject constructor(
    preferences: ConfiguracionPreferences,
) : Interceptor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var config: DeviceConfig = runBlocking { preferences.deviceConfig.first() }

    init {
        scope.launch {
            preferences.deviceConfig.collect { config = it }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val actual = config
        val host = actual.ip.trim()
        val puerto = actual.puerto.trim().toIntOrNull()
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
