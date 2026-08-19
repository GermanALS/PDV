package com.pdv.pos.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// No sigue el patron local_id/remote_id/is_synced/deleted_at: es en si
// misma el registro de auditoria de sync, no una entidad sincronizable
// (docs/schema-pos.json, tabla sync_conflicts).
@Entity(tableName = "sync_conflicts")
data class SyncConflictEntity(
    @PrimaryKey val id: String,
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
