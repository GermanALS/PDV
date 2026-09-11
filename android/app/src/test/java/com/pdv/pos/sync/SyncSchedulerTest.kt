package com.pdv.pos.sync

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.pdv.pos.domain.model.BackendMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncSchedulerTest {

    private val workManager: WorkManager = mockk(relaxed = true)
    private val scheduler = SyncScheduler(workManager)

    @Test
    fun `debeProgramarPeriodico solo es true en LOCAL_CON_SINCRONIZACION`() {
        assertTrue(SyncScheduler.debeProgramarPeriodico(BackendMode.LOCAL_CON_SINCRONIZACION))
        assertFalse(SyncScheduler.debeProgramarPeriodico(BackendMode.LOCAL))
        assertFalse(SyncScheduler.debeProgramarPeriodico(BackendMode.REMOTO))
    }

    @Test
    fun `LOCAL_CON_SINCRONIZACION encola el trabajo periodico unico`() {
        scheduler.actualizarProgramacion(BackendMode.LOCAL_CON_SINCRONIZACION)

        verify(exactly = 1) {
            workManager.enqueueUniquePeriodicWork(
                SyncScheduler.TRABAJO_PERIODICO,
                ExistingPeriodicWorkPolicy.KEEP,
                any<PeriodicWorkRequest>(),
            )
        }
        verify(exactly = 0) { workManager.cancelUniqueWork(any()) }
    }

    @Test
    fun `LOCAL puro cancela el trabajo periodico y no encola nada`() {
        scheduler.actualizarProgramacion(BackendMode.LOCAL)

        verify(exactly = 1) { workManager.cancelUniqueWork(SyncScheduler.TRABAJO_PERIODICO) }
        verify(exactly = 0) {
            workManager.enqueueUniquePeriodicWork(any(), any<ExistingPeriodicWorkPolicy>(), any<PeriodicWorkRequest>())
        }
    }

    @Test
    fun `REMOTO cancela el trabajo periodico y no encola nada`() {
        scheduler.actualizarProgramacion(BackendMode.REMOTO)

        verify(exactly = 1) { workManager.cancelUniqueWork(SyncScheduler.TRABAJO_PERIODICO) }
        verify(exactly = 0) {
            workManager.enqueueUniquePeriodicWork(any(), any<ExistingPeriodicWorkPolicy>(), any<PeriodicWorkRequest>())
        }
    }

    @Test
    fun `sincronizarAhora encola un trabajo one-time unico con REPLACE`() {
        scheduler.sincronizarAhora()

        // REPLACE, no KEEP: un disparo manual explicito siempre debe hacer
        // algo visible, aunque un intento anterior siga encolado/bloqueado
        // por constraints (PLAN.md Parte 32, hallazgo de verificacion en
        // dispositivo - con KEEP el boton parecia no hacer nada).
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                SyncScheduler.TRABAJO_INMEDIATO,
                ExistingWorkPolicy.REPLACE,
                any<androidx.work.OneTimeWorkRequest>(),
            )
        }
    }

    @Test
    fun `observarTrabajoInmediato expone el estado del ultimo work info`() = runTest {
        val workInfo = mockk<WorkInfo> { every { state } returns WorkInfo.State.RUNNING }
        every { workManager.getWorkInfosForUniqueWorkFlow(SyncScheduler.TRABAJO_INMEDIATO) } returns
            flowOf(listOf(workInfo))

        val estado = scheduler.observarTrabajoInmediato().first()

        assertEquals(WorkInfo.State.RUNNING, estado)
    }

    @Test
    fun `observarTrabajoInmediato es null cuando nunca se encolo nada`() = runTest {
        every { workManager.getWorkInfosForUniqueWorkFlow(SyncScheduler.TRABAJO_INMEDIATO) } returns flowOf(emptyList())

        assertNull(scheduler.observarTrabajoInmediato().first())
    }
}
