package com.pdv.pos.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.local.CajaDao
import com.pdv.pos.data.local.LocalCajaRepository
import com.pdv.pos.data.local.RetiroDao
import com.pdv.pos.data.local.VentaDao
import com.pdv.pos.data.remote.CajaApiService
import com.pdv.pos.data.remote.RemoteCajaRepository
import com.pdv.pos.data.remote.dto.CorteCajaDto
import com.pdv.pos.data.remote.dto.TotalesCorteDto
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.CorteCaja
import com.pdv.pos.logging.AppLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.math.BigDecimal

class ModeAwareCajaRepositoryTest {

    private fun preferences(tempDir: File): ConfiguracionPreferences {
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { File(tempDir, "test.preferences_pb") })
        return ConfiguracionPreferences(dataStore)
    }

    private fun corteDeEjemplo() = CorteCaja(
        id = "corte-1",
        sucursalId = "suc-1",
        usuarioId = "german",
        tipo = "parcial",
        fechaInicio = 0L,
        fechaFin = 100L,
        totalVentas = BigDecimal("100.00"),
        totalEfectivo = BigDecimal("100.00"),
        totalTarjeta = BigDecimal.ZERO,
        totalRetiros = BigDecimal.ZERO,
        montoEsperado = BigDecimal("100.00"),
        montoContado = null,
        diferencia = null,
    )

    @Test
    fun `switching BackendMode in DataStore switches which repository handles guardarCorte`(@TempDir tempDir: File) = runTest {
        val cajaDao = mockk<CajaDao>()
        coEvery { cajaDao.insertCorte(any()) } returns Unit
        val ventaDao = mockk<VentaDao>()
        val retiroDao = mockk<RetiroDao>()
        val appLogger = mockk<AppLogger>()
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val local = LocalCajaRepository(cajaDao, ventaDao, retiroDao, appLogger)

        val api = mockk<CajaApiService>()
        coEvery { api.createCorte(any()) } returns CorteCajaDto(
            id = "remote-1",
            sucursalId = "suc-1",
            usuarioId = "german",
            tipo = "parcial",
            fechaInicio = "2023-11-14T22:13:20Z",
            fechaFin = "2023-11-14T23:13:20Z",
            totalVentas = "100.00",
            totalEfectivo = "100.00",
            totalTarjeta = "0",
            totalRetiros = "0",
            montoEsperado = "100.00",
            updatedAt = "2023-11-14T23:13:20Z",
        )
        val remote = RemoteCajaRepository(api, appLogger)

        val preferences = preferences(tempDir)
        val repository = ModeAwareCajaRepository(local = local, remote = remote, preferences = preferences)

        repository.guardarCorte(corteDeEjemplo())
        coVerify(exactly = 1) { cajaDao.insertCorte(any()) }
        coVerify(exactly = 0) { api.createCorte(any()) }

        preferences.setBackendMode(BackendMode.REMOTO)
        repository.guardarCorte(corteDeEjemplo())

        coVerify(exactly = 1) { api.createCorte(any()) }
        coVerify(exactly = 1) { cajaDao.insertCorte(any()) }
    }

    @Test
    fun `switching BackendMode in DataStore switches which repository handles calcularTotales`(@TempDir tempDir: File) = runTest {
        val cajaDao = mockk<CajaDao>()
        val ventaDao = mockk<VentaDao>()
        coEvery { ventaDao.getVentasDelPeriodo(any(), any(), any()) } returns emptyList()
        val retiroDao = mockk<RetiroDao>()
        coEvery { retiroDao.getRetirosDelPeriodo(any(), any(), any()) } returns emptyList()
        val appLogger = mockk<AppLogger>()
        val local = LocalCajaRepository(cajaDao, ventaDao, retiroDao, appLogger)

        val api = mockk<CajaApiService>()
        coEvery { api.getTotales(any(), any(), any()) } returns TotalesCorteDto(
            totalVentas = "0",
            totalEfectivo = "0",
            totalTarjeta = "0",
            totalRetiros = "0",
            montoEsperado = "0",
        )
        val remote = RemoteCajaRepository(api, appLogger)

        val preferences = preferences(tempDir)
        val repository = ModeAwareCajaRepository(local = local, remote = remote, preferences = preferences)

        repository.calcularTotales("suc-1", 0L, 100L)
        coVerify(exactly = 1) { ventaDao.getVentasDelPeriodo(any(), any(), any()) }
        coVerify(exactly = 0) { api.getTotales(any(), any(), any()) }

        preferences.setBackendMode(BackendMode.REMOTO)
        repository.calcularTotales("suc-1", 0L, 100L)

        coVerify(exactly = 1) { api.getTotales(any(), any(), any()) }
        coVerify(exactly = 1) { ventaDao.getVentasDelPeriodo(any(), any(), any()) }
    }
}
