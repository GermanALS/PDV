package com.pdv.pos.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.pdv.pos.domain.model.BackendMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// Programa el SyncWorker (PLAN.md Parte 32, D2): un trabajo periodico mientras
// el modo sea LOCAL_CON_SINCRONIZACION, mas un one-time que dispara el boton
// "Sincronizar ahora" de Configuracion. En LOCAL puro y en REMOTO el
// periodico se cancela: el motor de sync diferido solo aplica a
// LOCAL_CON_SINCRONIZACION.
@Singleton
class SyncScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    fun actualizarProgramacion(mode: BackendMode) {
        if (debeProgramarPeriodico(mode)) {
            workManager.enqueueUniquePeriodicWork(
                TRABAJO_PERIODICO,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(INTERVALO_MINUTOS, TimeUnit.MINUTES)
                    .setConstraints(conectividad())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                    .build(),
            )
        } else {
            workManager.cancelUniqueWork(TRABAJO_PERIODICO)
        }
    }

    // REPLACE (no KEEP): es un disparo manual explicito - si un intento
    // anterior seguia encolado/bloqueado por constraints, el usuario no
    // tenia ninguna senal de eso y el boton parecia no hacer nada (PLAN.md
    // Parte 32, hallazgo de verificacion en dispositivo).
    fun sincronizarAhora() {
        workManager.enqueueUniqueWork(
            TRABAJO_INMEDIATO,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(conectividad())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    // Estado del ultimo "Sincronizar ahora" para que Configuracion pueda
    // mostrar progreso y refrescar los pendientes al terminar (mismo
    // hallazgo: sin esto, el boton no daba ninguna confirmacion visible).
    fun observarTrabajoInmediato(): Flow<WorkInfo.State?> =
        workManager.getWorkInfosForUniqueWorkFlow(TRABAJO_INMEDIATO).map { it.lastOrNull()?.state }

    private fun conectividad() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    companion object {
        const val TRABAJO_PERIODICO = "sync_periodico"
        const val TRABAJO_INMEDIATO = "sync_inmediato"
        const val INTERVALO_MINUTOS = 15L

        fun debeProgramarPeriodico(mode: BackendMode): Boolean =
            mode == BackendMode.LOCAL_CON_SINCRONIZACION
    }
}
