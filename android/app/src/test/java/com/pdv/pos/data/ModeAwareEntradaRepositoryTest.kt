package com.pdv.pos.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.local.EntradaDao
import com.pdv.pos.data.local.LocalEntradaRepository
import com.pdv.pos.data.remote.EntradaApiService
import com.pdv.pos.data.remote.RemoteEntradaRepository
import com.pdv.pos.data.remote.dto.EntradaDto
import com.pdv.pos.data.remote.dto.InventarioDto
import com.pdv.pos.data.remote.dto.MovimientoDto
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.Entrada
import com.pdv.pos.logging.AppLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.math.BigDecimal

class ModeAwareEntradaRepositoryTest {

    private fun preferences(tempDir: File): ConfiguracionPreferences {
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { File(tempDir, "test.preferences_pb") })
        return ConfiguracionPreferences(dataStore)
    }

    private fun entradaDeEjemplo() = Entrada.DeArticuloExistente(
        id = "entrada-1",
        sucursalId = "suc-1",
        usuarioId = "german",
        fecha = 1_700_000_000_000L,
        cantidad = BigDecimal("5"),
        ubicacion = "Estante A1",
        articuloId = "art-1",
    )

    @Test
    fun `switching BackendMode in DataStore switches which repository handles registrarEntrada`(@TempDir tempDir: File) = runTest {
        val dao = mockk<EntradaDao>()
        coEvery { dao.insertEntradaCompleta(any(), any(), any(), any(), any(), any(), any()) } returns Unit
        val appLogger = mockk<AppLogger>()
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val local = LocalEntradaRepository(dao, appLogger)

        val api = mockk<EntradaApiService>()
        coEvery { api.createEntrada(any()) } returns EntradaDto(
            movimiento = MovimientoDto(
                id = "mov-1",
                sucursalId = "suc-1",
                articuloId = "art-1",
                usuarioId = "german",
                tipo = "entrada",
                cantidad = "5.000",
                fecha = "2023-11-14T22:13:20Z",
                updatedAt = "2023-11-14T22:13:20Z",
            ),
            inventario = InventarioDto(
                id = "inv-1",
                sucursalId = "suc-1",
                articuloId = "art-1",
                cantidad = "5.000",
                updatedAt = "2023-11-14T22:13:20Z",
            ),
        )
        val remote = RemoteEntradaRepository(api, appLogger)

        val preferences = preferences(tempDir)
        val repository = ModeAwareEntradaRepository(local = local, remote = remote, preferences = preferences)

        repository.registrarEntrada(entradaDeEjemplo())
        coVerify(exactly = 1) { dao.insertEntradaCompleta(any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { api.createEntrada(any()) }

        preferences.setBackendMode(BackendMode.REMOTO)
        repository.registrarEntrada(entradaDeEjemplo())

        coVerify(exactly = 1) { api.createEntrada(any()) }
        coVerify(exactly = 1) { dao.insertEntradaCompleta(any(), any(), any(), any(), any(), any(), any()) }
    }
}
