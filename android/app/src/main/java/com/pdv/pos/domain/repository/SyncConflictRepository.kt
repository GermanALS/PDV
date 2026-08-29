package com.pdv.pos.domain.repository

import com.pdv.pos.domain.model.SyncConflict
import kotlinx.coroutines.flow.Flow

/**
 * Acceso de solo lectura a los conflictos de sincronización (PLAN.md
 * Parte 19, decisión "solo lectura"). No expone escritura: el
 * administrador solo audita.
 */
interface SyncConflictRepository {
    fun observeConflictos(): Flow<List<SyncConflict>>
}
