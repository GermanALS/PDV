package com.pdv.pos.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.caja.CajaRefreshSignal
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.local.LocalRetiroEfectivoRepository
import com.pdv.pos.data.local.RetiroDao
import com.pdv.pos.data.remote.RemoteRetiroEfectivoRepository
import com.pdv.pos.data.remote.RetiroApiService
import com.pdv.pos.data.remote.dto.RetiroEfectivoDto
import com.pdv.pos.data.remote.dto.RetiroEfectivoListResponseDto
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.RetiroEfectivo
import com.pdv.pos.logging.AppLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.math.BigDecimal

class ModeAwareRetiroEfectivoRepositoryTest {

    private fun preferences(tempDir: File): ConfiguracionPreferences {
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { File(tempDir, "test.preferences_pb") })
        return ConfiguracionPreferences(dataStore)
    }

    private fun retiroDeEjemplo() = RetiroEfectivo(
        id = "retiro-1",
        sucursalId = "suc-1",
        usuarioId = "german",
        monto = BigDecimal("100.00"),
        motivo = null,
        fecha = 1_700_000_000_000L,
    )

    @Test
    fun `switching BackendMode in DataStore switches which repository handles registrarRetiro`(@TempDir tempDir: File) = runTest {
        val dao = mockk<RetiroDao>()
        coEvery { dao.insertRetiro(any()) } returns Unit
        val appLogger = mockk<AppLogger>()
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val local = LocalRetiroEfectivoRepository(dao, appLogger)

        val api = mockk<RetiroApiService>()
        coEvery { api.createRetiro(any()) } returns RetiroEfectivoDto(
            id = "remote-1",
            sucursalId = "suc-1",
            usuarioId = "german",
            monto = "100.00",
            fecha = "2023-11-14T22:13:20Z",
            updatedAt = "2023-11-14T22:13:20Z",
        )
        val remote = RemoteRetiroEfectivoRepository(api, appLogger)

        val preferences = preferences(tempDir)
        val repository = ModeAwareRetiroEfectivoRepository(
            local = local,
            remote = remote,
            preferences = preferences,
            cajaRefreshSignal = CajaRefreshSignal(),
        )

        repository.registrarRetiro(retiroDeEjemplo())
        coVerify(exactly = 1) { dao.insertRetiro(any()) }
        coVerify(exactly = 0) { api.createRetiro(any()) }

        preferences.setBackendMode(BackendMode.REMOTO)
        repository.registrarRetiro(retiroDeEjemplo())

        coVerify(exactly = 1) { api.createRetiro(any()) }
        coVerify(exactly = 1) { dao.insertRetiro(any()) }
    }

    // PLAN.md Parte 18, sub-parte F: mismo CajaRefreshSignal que
    // ModeAwareCajaRepository - registrarRetiro tambien lo emite.
    @Test
    fun `registrarRetiro exitoso emite la señal de refresco de caja`(@TempDir tempDir: File) = runTest {
        val dao = mockk<RetiroDao>()
        coEvery { dao.insertRetiro(any()) } returns Unit
        val appLogger = mockk<AppLogger>()
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val local = LocalRetiroEfectivoRepository(dao, appLogger)
        val remote = mockk<RemoteRetiroEfectivoRepository>(relaxed = true)
        val signal = CajaRefreshSignal()
        val repository = ModeAwareRetiroEfectivoRepository(
            local = local,
            remote = remote,
            preferences = preferences(tempDir),
            cajaRefreshSignal = signal,
        )

        repository.registrarRetiro(retiroDeEjemplo())

        withTimeout(1000) { signal.refrescos.first() }
    }

    @Test
    fun `switching BackendMode in DataStore switches which repository handles observeRetiros`(@TempDir tempDir: File) = runTest {
        val dao = mockk<RetiroDao>()
        every { dao.observarRetiros(any()) } returns flowOf(emptyList())
        val appLogger = mockk<AppLogger>()
        val local = LocalRetiroEfectivoRepository(dao, appLogger)

        val api = mockk<RetiroApiService>()
        coEvery { api.getRetiros(any(), any(), any()) } returns RetiroEfectivoListResponseDto(
            items = emptyList(),
            page = 1,
            pageSize = 50,
            total = 0,
        )
        val remote = RemoteRetiroEfectivoRepository(api, appLogger)

        val preferences = preferences(tempDir)
        val repository = ModeAwareRetiroEfectivoRepository(
            local = local,
            remote = remote,
            preferences = preferences,
            cajaRefreshSignal = CajaRefreshSignal(),
        )

        assertEquals(emptyList<RetiroEfectivo>(), repository.observeRetiros("suc-1").first())
        coVerify(exactly = 0) { api.getRetiros(any(), any(), any()) }

        preferences.setBackendMode(BackendMode.REMOTO)

        assertEquals(emptyList<RetiroEfectivo>(), repository.observeRetiros("suc-1").first())
        coVerify(exactly = 1) { api.getRetiros("suc-1", 1, 50) }
    }
}
