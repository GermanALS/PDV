package com.pdv.pos.data.remote

import com.pdv.pos.domain.model.EsquemaConexion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// Prueba puntual de alcance del backend contra una URL explicita, para el
// boton "Probar conexion" del panel del login y de Configuracion (PLAN.md
// Parte 31). Cliente propio sin DynamicHostInterceptor ni AuthInterceptor: la
// prueba usa exactamente el esquema/host/puerto tecleados, sin que el
// interceptor los reescriba con lo que haya persistido, y sin token (el panel
// se usa sin sesion).
@Singleton
class BackendHealthChecker @Inject constructor() {

    private val client = OkHttpClient()

    suspend fun probar(esquema: EsquemaConexion, host: String, puerto: String): ApiResult<String> {
        val puertoValido = puerto.trim().toIntOrNull()
            ?: return ApiResult.Error("Puerto invalido: \"$puerto\"")
        if (host.isBlank()) return ApiResult.Error("Falta el host")
        val url = "${esquema.scheme()}://${host.trim()}:$puertoValido/api/v1/health"
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (response.isSuccessful) {
                        ApiResult.Success(response.body?.string().orEmpty().take(200))
                    } else {
                        ApiResult.Error("El backend respondio HTTP ${response.code}")
                    }
                }
            } catch (e: IOException) {
                ApiResult.Error("No se pudo conectar a $url: ${e.message}")
            }
        }
    }
}
