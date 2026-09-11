package com.pdv.pos.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

// Ejecuta un ciclo del motor de sync diferido (PLAN.md Parte 32). Es delgado
// a proposito: la logica vive en SyncOrchestrator; aqui solo se aplica la
// guarda de modo y se traduce el resultado a un WorkManager Result.
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val preferences: ConfiguracionPreferences,
    private val orchestrator: SyncOrchestrator,
    private val syncStateStore: SyncStateStore,
    private val appLogger: AppLogger,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // El usuario pudo cambiar de modo entre el enqueue y esta ejecucion.
        if (preferences.deviceConfig.first().backendMode != BackendMode.LOCAL_CON_SINCRONIZACION) {
            return Result.success()
        }

        return when (val resultado = orchestrator.sincronizar()) {
            is SyncResultado.Exito -> {
                syncStateStore.registrarExito(System.currentTimeMillis())
                Result.success()
            }

            is SyncResultado.Reintentar -> {
                syncStateStore.registrarError(resultado.motivo)
                Result.retry()
            }

            is SyncResultado.Fallo -> {
                syncStateStore.registrarError(resultado.motivo)
                appLogger.log(
                    LogType.ERROR,
                    sucursalId = "-",
                    usuario = "system",
                    mensaje = "Sync detenido: ${resultado.motivo}",
                )
                Result.failure()
            }
        }
    }
}
