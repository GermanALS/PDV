package com.pdv.pos.sync.push

import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import retrofit2.HttpException

// Sube al backend las filas de una entidad transaccional creadas offline
// (PLAN.md Parte 32, Grupo 2). Reintento seguro por la idempotencia
// UNIQUE(local_id) del backend (Parte 23): re-subir una fila ya persistida
// devuelve 200 con la fila existente.
interface EntityPusher {
    // Nombre legible para el resumen de pendientes del cambio de modo.
    val nombre: String

    suspend fun contarPendientes(): Int

    // Sube todas las filas pendientes. Propaga IOException y HttpException
    // 401/5xx para que el orquestador reintente el ciclo o lo detenga; una
    // fila que falla con 4xx (rol sin permiso de modulo, sku duplicado,
    // payload invalido) se descarta y se sigue con el resto.
    suspend fun empujarPendientes(): PushResultado
}

data class PushResultado(val subidas: Int, val saltadas: Int) {
    operator fun plus(otro: PushResultado) = PushResultado(subidas + otro.subidas, saltadas + otro.saltadas)

    companion object {
        val VACIO = PushResultado(subidas = 0, saltadas = 0)
    }
}

// Ejecuta la subida de una fila. Devuelve null (y loguea) si el backend
// rechaza la fila con un 4xx que no sea 401 - esa fila se descarta y el ciclo
// sigue. IOException y HttpException 401/5xx se propagan.
suspend fun <T> empujarFila(
    appLogger: AppLogger,
    descripcion: String,
    bloque: suspend () -> T,
): T? = try {
    bloque()
} catch (e: HttpException) {
    if (e.code() == 401 || e.code() >= 500) throw e
    appLogger.log(
        LogType.ERROR,
        sucursalId = "-",
        usuario = "system",
        mensaje = "Sync: fila descartada ($descripcion): HTTP ${e.code()}",
    )
    null
}
