package com.pdv.pos.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.local.DevolucionDao
import com.pdv.pos.data.local.LocalDevolucionRepository
import com.pdv.pos.data.remote.DevolucionApiService
import com.pdv.pos.data.remote.RemoteDevolucionRepository
import com.pdv.pos.data.remote.dto.DevolucionDetalleDto
import com.pdv.pos.data.remote.dto.DevolucionDto
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.Devolucion
import com.pdv.pos.domain.model.DevolucionLinea
import com.pdv.pos.logging.AppLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.math.BigDecimal

class ModeAwareDevolucionRepositoryTest {

    private fun preferences(tempDir: File): ConfiguracionPreferences {
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { File(tempDir, "test.preferences_pb") })
        return ConfiguracionPreferences(dataStore)
    }

    private fun devolucionDeEjemplo() = Devolucion(
        id = "devolucion-1",
        sucursalId = "suc-1",
        usuarioId = "german",
        ventaId = null,
        folio = "D-001",
        fecha = 1_700_000_000_000L,
        estado = "registrada",
        lineas = listOf(
            DevolucionLinea(
                articuloId = "art-1",
                cantidad = BigDecimal("1"),
                motivo = null,
                condicion = "defectuoso",
            ),
        ),
    )

    @Test
    fun `switching BackendMode in DataStore switches which repository handles registrarDevolucion`(@TempDir tempDir: File) = runTest {
        val dao = mockk<DevolucionDao>()
        coEvery { dao.insertDevolucionCompleta(any(), any()) } returns Unit
        val appLogger = mockk<AppLogger>()
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val local = LocalDevolucionRepository(dao, appLogger)

        val api = mockk<DevolucionApiService>()
        coEvery { api.createDevolucion(any()) } returns DevolucionDto(
            id = "remote-1",
            sucursalId = "suc-1",
            usuarioId = "german",
            folio = "D-001",
            fecha = "2023-11-14T22:13:20Z",
            estado = "registrada",
            updatedAt = "2023-11-14T22:13:20Z",
            lineas = listOf(
                DevolucionDetalleDto(
                    id = "det-1",
                    articuloId = "art-1",
                    cantidad = "1",
                    condicion = "defectuoso",
                ),
            ),
        )
        val remote = RemoteDevolucionRepository(api, appLogger)

        val preferences = preferences(tempDir)
        val repository = ModeAwareDevolucionRepository(local = local, remote = remote, preferences = preferences)

        repository.registrarDevolucion(devolucionDeEjemplo())
        coVerify(exactly = 1) { dao.insertDevolucionCompleta(any(), any()) }
        coVerify(exactly = 0) { api.createDevolucion(any()) }

        preferences.setBackendMode(BackendMode.REMOTO)
        repository.registrarDevolucion(devolucionDeEjemplo())

        coVerify(exactly = 1) { api.createDevolucion(any()) }
        coVerify(exactly = 1) { dao.insertDevolucionCompleta(any(), any()) }
    }
}
