package com.pdv.pos.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.local.LocalVentaRepository
import com.pdv.pos.data.local.VentaDao
import com.pdv.pos.data.remote.RemoteVentaRepository
import com.pdv.pos.data.remote.VentaApiService
import com.pdv.pos.data.remote.dto.VentaDetalleDto
import com.pdv.pos.data.remote.dto.VentaDto
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.Venta
import com.pdv.pos.domain.model.VentaLinea
import com.pdv.pos.logging.AppLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.math.BigDecimal

class ModeAwareVentaRepositoryTest {

    private fun preferences(tempDir: File): ConfiguracionPreferences {
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { File(tempDir, "test.preferences_pb") })
        return ConfiguracionPreferences(dataStore)
    }

    private fun ventaDeEjemplo() = Venta(
        id = "venta-1",
        sucursalId = "suc-1",
        usuarioId = "german",
        folio = "F-001",
        fecha = 1_700_000_000_000L,
        subtotal = BigDecimal("50.00"),
        descuento = BigDecimal.ZERO,
        impuestos = BigDecimal.ZERO,
        total = BigDecimal("50.00"),
        metodoPago = "efectivo",
        estado = "completada",
        lineas = listOf(
            VentaLinea(
                articuloId = "art-1",
                cantidad = BigDecimal("1"),
                precioUnitario = BigDecimal("50.00"),
                subtotal = BigDecimal("50.00"),
            ),
        ),
    )

    @Test
    fun `switching BackendMode in DataStore switches which repository handles registrarVenta`(@TempDir tempDir: File) = runTest {
        val dao = mockk<VentaDao>()
        coEvery { dao.insertVentaCompleta(any(), any(), any()) } returns Unit
        val appLogger = mockk<AppLogger>()
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val local = LocalVentaRepository(dao, appLogger)

        val api = mockk<VentaApiService>()
        coEvery { api.createVenta(any()) } returns VentaDto(
            id = "remote-1",
            sucursalId = "suc-1",
            usuarioId = "german",
            folio = "F-001",
            fecha = "2023-11-14T22:13:20Z",
            subtotal = "50.00",
            descuento = "0",
            impuestos = "0",
            total = "50.00",
            metodoPago = "efectivo",
            estado = "completada",
            updatedAt = "2023-11-14T22:13:20Z",
            lineas = listOf(
                VentaDetalleDto(
                    id = "det-1",
                    articuloId = "art-1",
                    cantidad = "1",
                    precioUnitario = "50.00",
                    subtotal = "50.00",
                ),
            ),
        )
        val remote = RemoteVentaRepository(api, appLogger)

        val preferences = preferences(tempDir)
        val repository = ModeAwareVentaRepository(local = local, remote = remote, preferences = preferences)

        repository.registrarVenta(ventaDeEjemplo())
        coVerify(exactly = 1) { dao.insertVentaCompleta(any(), any(), any()) }
        coVerify(exactly = 0) { api.createVenta(any()) }

        preferences.setBackendMode(BackendMode.REMOTO)
        repository.registrarVenta(ventaDeEjemplo())

        coVerify(exactly = 1) { api.createVenta(any()) }
        coVerify(exactly = 1) { dao.insertVentaCompleta(any(), any(), any()) }
    }
}
