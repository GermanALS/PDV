package com.pdv.pos.sync.push

import com.pdv.pos.data.local.SyncConflictDao
import com.pdv.pos.data.local.SyncConflictEntity
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import retrofit2.HttpException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

const val POLITICA_RECHAZO_BACKEND = "rechazo_backend"

// Fila que se intenta subir: identifica el conflicto que se registra si el
// backend la rechaza.
data class FilaSync(val entidad: String, val localId: String, val sucursalId: String?, val descripcion: String)

// Rechazos visibles del push (PLAN.md Parte 36, A-6): una fila que el backend
// rechaza con un 4xx distinto de 401 se marca rechazada con su codigo (nunca
// isSynced), se registra en sync_conflicts para el panel de la Parte 19 y no
// se reintenta en cada ciclo.
@Singleton
class RegistroRechazos @Inject constructor(
    private val syncConflictDao: SyncConflictDao,
    private val appLogger: AppLogger,
) {
    // Ejecuta la subida de una fila. Devuelve null si el backend la rechaza
    // con un 4xx no-401, despues de marcarla con marcarRechazada(codigo).
    // IOException y HttpException 401/5xx se propagan.
    suspend fun <T> empujarFila(
        fila: FilaSync,
        marcarRechazada: suspend (codigo: Int) -> Unit,
        bloque: suspend () -> T,
    ): T? = try {
        bloque()
    } catch (e: HttpException) {
        if (e.code() == 401 || e.code() >= 500) throw e
        marcarRechazada(e.code())
        registrar(fila, e.code())
        null
    }

    private suspend fun registrar(fila: FilaSync, codigo: Int) {
        syncConflictDao.insert(
            SyncConflictEntity(
                id = UUID.randomUUID().toString(),
                entidad = fila.entidad,
                entidadLocalId = fila.localId,
                sucursalId = fila.sucursalId,
                valorLocal = fila.descripcion,
                valorRemoto = "HTTP $codigo",
                valorResuelto = "rechazada, sin subir",
                politicaAplicada = POLITICA_RECHAZO_BACKEND,
                resueltoAutomaticamente = false,
                fechaDeteccion = System.currentTimeMillis(),
            ),
        )
        appLogger.log(
            LogType.SYNC_CONFLICT,
            sucursalId = fila.sucursalId ?: "-",
            usuario = "system",
            mensaje = "Sync: ${fila.descripcion} rechazada por el backend: HTTP $codigo",
        )
    }
}
