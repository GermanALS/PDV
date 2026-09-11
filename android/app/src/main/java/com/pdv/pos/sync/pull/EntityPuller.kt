package com.pdv.pos.sync.pull

// Baja del backend lo cambiado (por otras terminales de la misma sucursal)
// desde el ultimo ciclo y lo mergea a Room (PLAN.md Parte 32, Grupo 3). El
// filtro es `?updated_since=` con el cursor guardado en SyncStateStore;
// tras mergear todas las paginas, avanza el cursor al `max(updated_at)`
// visto. Propaga IOException / HttpException para que el orquestador mapee.
interface EntityPuller {
    // Clave del cursor en SyncStateStore.
    val entidad: String

    suspend fun pull(sucursalId: String)
}

internal const val PULL_PAGE_SIZE = 100
