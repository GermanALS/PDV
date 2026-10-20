package com.pdv.pos.sync.push

// Sube al backend las filas de una entidad transaccional creadas offline
// (PLAN.md Parte 32, Grupo 2). Reintento seguro por la idempotencia
// UNIQUE(local_id) del backend (Parte 23): re-subir una fila ya persistida
// devuelve 200 con la fila existente.
interface EntityPusher {
    // Nombre legible para el resumen de pendientes del cambio de modo.
    val nombre: String

    suspend fun contarPendientes(): Int

    // Filas que el backend rechazo con un 4xx (PLAN.md Parte 36, A-6).
    suspend fun contarRechazadas(): Int

    // Sube todas las filas pendientes. Propaga IOException y HttpException
    // 401/5xx para que el orquestador reintente el ciclo o lo detenga; una
    // fila que falla con 4xx (rol sin permiso de modulo, sku duplicado,
    // payload invalido) queda rechazada (RegistroRechazos) y se sigue con el
    // resto.
    suspend fun empujarPendientes(): PushResultado
}

data class PushResultado(val subidas: Int, val saltadas: Int) {
    operator fun plus(otro: PushResultado) = PushResultado(subidas + otro.subidas, saltadas + otro.saltadas)

    companion object {
        val VACIO = PushResultado(subidas = 0, saltadas = 0)
    }
}
