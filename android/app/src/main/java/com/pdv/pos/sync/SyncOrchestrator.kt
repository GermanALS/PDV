package com.pdv.pos.sync

// Punto de entrada del motor de sync diferido (PLAN.md Parte 32). Un ciclo
// sube lo creado offline (push) y luego baja los cambios remotos (pull). La
// implementacion real se construye en los Grupos 2 y 3; el SyncWorker solo la
// invoca y traduce el resultado a un WorkManager Result.
interface SyncOrchestrator {
    suspend fun sincronizar(): SyncResultado
}

sealed interface SyncResultado {
    // Ciclo completo sin errores.
    data object Exito : SyncResultado

    // Fallo transitorio (red): reintentar el ciclo mas tarde con backoff.
    data class Reintentar(val motivo: String) : SyncResultado

    // Fallo no recuperable reintentando (ej. sesion expirada): parar el ciclo.
    data class Fallo(val motivo: String) : SyncResultado
}
