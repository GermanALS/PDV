package com.pdv.pos.sync

import com.pdv.pos.data.local.SyncConflictDao
import com.pdv.pos.data.local.SyncConflictEntity
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val POLITICA_EVENTO_ADITIVO = "evento_aditivo_delta"
private const val POLITICA_LWW_LOCAL_GANA = "last_write_wins_local_gana"

// Merge de cantidades de inventario en el pull del motor de sync diferido
// (PLAN.md Parte 32, Grupo 3): aplica siempre el delta con signo y, si el
// resultado deja la cantidad negativa, registra un conflicto NO
// auto-resuelto en sync_conflicts + log SYNC_CONFLICT (D4: regla fija, sin
// UI de resolucion; el panel de la Parte 19 lo muestra solo-lectura y un
// admin lo corrige con un ajuste de la Parte 9).
@Singleton
class EventoAditivoSyncEngine @Inject constructor(
    private val syncConflictDao: SyncConflictDao,
    private val appLogger: AppLogger,
) {
    suspend fun combinarYRegistrar(
        entidad: String,
        entidadLocalId: String,
        sucursalId: String?,
        usuario: String,
        base: BigDecimal,
        deltaLocal: BigDecimal,
        deltaRemoto: BigDecimal,
    ): BigDecimal {
        val resultado = EventoAditivoCombiner.combinarConDeteccion(base, deltaLocal, deltaRemoto)
        if (resultado.quedoNegativo) {
            registrarConflicto(entidad, entidadLocalId, sucursalId, usuario, base, deltaLocal, deltaRemoto, resultado.valor)
        }
        return resultado.valor
    }

    // Pull (Grupo 3): la fila local tiene cambios sin subir y el valor remoto
    // ademas difiere. Se conserva lo local (se subira en el push) y se deja
    // constancia del cruce para revision manual (D4: sin UI de resolucion).
    suspend fun registrarDivergenciaLocalGana(
        entidad: String,
        entidadLocalId: String,
        sucursalId: String?,
        usuario: String,
        valorLocal: BigDecimal,
        valorRemoto: BigDecimal,
    ) {
        syncConflictDao.insert(
            SyncConflictEntity(
                id = UUID.randomUUID().toString(),
                entidad = entidad,
                entidadLocalId = entidadLocalId,
                sucursalId = sucursalId,
                valorLocal = valorLocal.toPlainString(),
                valorRemoto = valorRemoto.toPlainString(),
                valorResuelto = valorLocal.toPlainString(),
                politicaAplicada = POLITICA_LWW_LOCAL_GANA,
                resueltoAutomaticamente = false,
                fechaDeteccion = System.currentTimeMillis(),
            ),
        )
        appLogger.log(
            LogType.SYNC_CONFLICT,
            sucursalId = sucursalId ?: "-",
            usuario = usuario,
            mensaje = "Conflicto en $entidad ($entidadLocalId): la fila local sin subir " +
                "(local=$valorLocal) diverge del backend (remoto=$valorRemoto); gana lo local hasta el proximo push",
        )
    }

    private suspend fun registrarConflicto(
        entidad: String,
        entidadLocalId: String,
        sucursalId: String?,
        usuario: String,
        base: BigDecimal,
        deltaLocal: BigDecimal,
        deltaRemoto: BigDecimal,
        valorResuelto: BigDecimal,
    ) {
        syncConflictDao.insert(
            SyncConflictEntity(
                id = UUID.randomUUID().toString(),
                entidad = entidad,
                entidadLocalId = entidadLocalId,
                sucursalId = sucursalId,
                valorLocal = deltaLocal.toPlainString(),
                valorRemoto = deltaRemoto.toPlainString(),
                valorResuelto = valorResuelto.toPlainString(),
                politicaAplicada = POLITICA_EVENTO_ADITIVO,
                resueltoAutomaticamente = false,
                fechaDeteccion = System.currentTimeMillis(),
            ),
        )
        appLogger.log(
            LogType.SYNC_CONFLICT,
            sucursalId = sucursalId ?: "-",
            usuario = usuario,
            mensaje = "Conflicto en $entidad ($entidadLocalId): el delta dejo la cantidad negativa " +
                "(base=$base local=$deltaLocal remoto=$deltaRemoto -> $valorResuelto), requiere ajuste manual",
        )
    }
}
