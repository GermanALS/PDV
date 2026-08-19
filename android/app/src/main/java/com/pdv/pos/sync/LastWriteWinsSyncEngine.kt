package com.pdv.pos.sync

import com.pdv.pos.data.local.SyncConflictDao
import com.pdv.pos.data.local.SyncConflictEntity
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val POLITICA_LAST_WRITE_WINS = "last_write_wins"

// Motor generico de last-write-wins (PLAN.md Parte 6): cualquier
// divergencia de updatedAt entre local y remoto se registra como
// conflicto auditable en sync_conflicts, incluso cuando se resuelve
// automaticamente (PLAN.md Parte 3, tabla sync_conflicts).
@Singleton
class LastWriteWinsSyncEngine @Inject constructor(
    private val syncConflictDao: SyncConflictDao,
    private val appLogger: AppLogger,
) {
    suspend fun <T> sincronizar(
        entidad: String,
        entidadLocalId: String,
        sucursalId: String?,
        usuario: String,
        local: Versioned<T>,
        remote: Versioned<T>,
        serializar: (T) -> String,
    ): T {
        val ganador = LastWriteWinsResolver.resolve(local, remote)
        if (local.updatedAt != remote.updatedAt) {
            registrarConflicto(
                entidad = entidad,
                entidadLocalId = entidadLocalId,
                sucursalId = sucursalId,
                usuario = usuario,
                valorLocal = serializar(local.value),
                valorRemoto = serializar(remote.value),
                valorResuelto = serializar(ganador.value),
            )
        }
        return ganador.value
    }

    private suspend fun registrarConflicto(
        entidad: String,
        entidadLocalId: String,
        sucursalId: String?,
        usuario: String,
        valorLocal: String,
        valorRemoto: String,
        valorResuelto: String,
    ) {
        syncConflictDao.insert(
            SyncConflictEntity(
                id = UUID.randomUUID().toString(),
                entidad = entidad,
                entidadLocalId = entidadLocalId,
                sucursalId = sucursalId,
                valorLocal = valorLocal,
                valorRemoto = valorRemoto,
                valorResuelto = valorResuelto,
                politicaAplicada = POLITICA_LAST_WRITE_WINS,
                resueltoAutomaticamente = true,
                fechaDeteccion = System.currentTimeMillis(),
            )
        )
        appLogger.log(
            LogType.SYNC_CONFLICT,
            sucursalId = sucursalId ?: "-",
            usuario = usuario,
            mensaje = "Conflicto en $entidad ($entidadLocalId), politica $POLITICA_LAST_WRITE_WINS: local=$valorLocal remoto=$valorRemoto resuelto=$valorResuelto",
        )
    }
}
