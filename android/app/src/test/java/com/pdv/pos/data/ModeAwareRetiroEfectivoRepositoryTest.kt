package com.pdv.pos.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.local.LocalRetiroEfectivoRepository
import com.pdv.pos.data.local.RetiroDao
import com.pdv.pos.data.remote.RemoteRetiroEfectivoRepository
import com.pdv.pos.data.remote.RetiroApiService
import com.pdv.pos.data.remote.dto.RetiroEfectivoDto
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.RetiroEfectivo
import com.pdv.pos.logging.AppLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
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
        val repository = ModeAwareRetiroEfectivoRepository(local = local, remote = remote, preferences = preferences)

        repository.registrarRetiro(retiroDeEjemplo())
        coVerify(exactly = 1) { dao.insertRetiro(any()) }
        coVerify(exactly = 0) { api.createRetiro(any()) }

        preferences.setBackendMode(BackendMode.REMOTO)
        repository.registrarRetiro(retiroDeEjemplo())

        coVerify(exactly = 1) { api.createRetiro(any()) }
        coVerify(exactly = 1) { dao.insertRetiro(any()) }
    }
}
