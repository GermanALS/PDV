package com.pdv.pos.conflictos

/**
 * Estado de la pantalla de revisión de conflictos de sincronización
 * (PLAN.md Parte 19). Solo lectura: audita los registros de `sync_conflicts`
 * generados por el motor de sync (Parte 6), incluidos los auto-resueltos.
 */
data class RevisionConflictosUiState(
    val conflictos: List<ConflictoUi> = emptyList(),
    val filtro: FiltroConflicto = FiltroConflicto.TODOS,
) {
    val conflictosFiltrados: List<ConflictoUi>
        get() = when (filtro) {
            FiltroConflicto.TODOS -> conflictos
            FiltroConflicto.PENDIENTES -> conflictos.filter { !it.resueltoAutomaticamente }
            FiltroConflicto.AUTO_RESUELTOS -> conflictos.filter { it.resueltoAutomaticamente }
        }
}

enum class FiltroConflicto { TODOS, PENDIENTES, AUTO_RESUELTOS }

/**
 * Modelo de presentación de un conflicto. Mapea 1:1 a `SyncConflictEntity`
 * (Parte 6); `fechaDeteccion` llega ya formateada y los tres valores son el
 * JSON crudo del snapshot.
 */
data class ConflictoUi(
    val entidad: String,
    val fechaDeteccion: String,
    val politicaAplicada: String,
    val entidadLocalId: String,
    val sucursalId: String?,
    val valorLocal: String,
    val valorRemoto: String,
    val valorResuelto: String,
    val resueltoAutomaticamente: Boolean,
)
