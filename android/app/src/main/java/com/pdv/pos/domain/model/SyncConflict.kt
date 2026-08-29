package com.pdv.pos.domain.model

/**
 * Registro de un conflicto de sincronización (tabla `sync_conflicts`,
 * PLAN.md Parte 3) generado por el motor de sync (Parte 6). Auditoría de
 * solo lectura: el panel de la Parte 19 lo lista, nunca lo modifica.
 * `valorLocal`/`valorRemoto`/`valorResuelto` son el JSON crudo del snapshot.
 */
data class SyncConflict(
    val id: String,
    val entidad: String,
    val entidadLocalId: String,
    val sucursalId: String?,
    val valorLocal: String,
    val valorRemoto: String,
    val valorResuelto: String,
    val politicaAplicada: String,
    val resueltoAutomaticamente: Boolean,
    val fechaDeteccion: Long,
)
