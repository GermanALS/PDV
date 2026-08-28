package com.pdv.pos.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.caja.CajaRefreshSignal
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.local.CajaDao
import com.pdv.pos.data.local.LocalCajaRepository
import com.pdv.pos.data.local.RetiroDao
import com.pdv.pos.data.local.VentaDao
import com.pdv.pos.data.remote.CajaApiService
import com.pdv.pos.data.remote.RemoteCajaRepository
import com.pdv.pos.data.remote.dto.CorteCajaDto
import com.pdv.pos.data.remote.dto.CorteCajaListResponseDto
import com.pdv.pos.data.remote.dto.TotalesCorteDto
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.CorteCaja
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
        val repository = ModeAwareCajaRepository(
            local = local,
            remote = remote,
            preferences = preferences,
            cajaRefreshSignal = CajaRefreshSignal(),
        )

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
        val repository = ModeAwareCajaRepository(
            local = local,
            remote = remote,
            preferences = preferences,
            cajaRefreshSignal = CajaRefreshSignal(),
        )

        repository.calcularTotales("suc-1", 0L, 100L)
        coVerify(exactly = 1) { ventaDao.getVentasDelPeriodo(any(), any(), any()) }
        coVerify(exactly = 0) { api.getTotales(any(), any(), any()) }

        preferences.setBackendMode(BackendMode.REMOTO)
        repository.calcularTotales("suc-1", 0L, 100L)

        coVerify(exactly = 1) { api.getTotales(any(), any(), any()) }
        coVerify(exactly = 1) { ventaDao.getVentasDelPeriodo(any(), any(), any()) }
    }

    // PLAN.md Parte 18, sub-parte F: unico punto de emision de
    // CajaRefreshSignal para cortes (cubre tanto la pantalla manual como
    // corte_parcial de la IA, que llama a este mismo metodo).
    @Test
    fun `guardarCorte exitoso emite la señal de refresco de caja`(@TempDir tempDir: File) = runTest {
        val cajaDao = mockk<CajaDao>()
        coEvery { cajaDao.insertCorte(any()) } returns Unit
        val ventaDao = mockk<VentaDao>()
        val retiroDao = mockk<RetiroDao>()
        val appLogger = mockk<AppLogger>()
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val local = LocalCajaRepository(cajaDao, ventaDao, retiroDao, appLogger)
        val remote = mockk<RemoteCajaRepository>(relaxed = true)
        val signal = CajaRefreshSignal()
        val repository = ModeAwareCajaRepository(
            local = local,
            remote = remote,
            preferences = preferences(tempDir),
            cajaRefreshSignal = signal,
        )

        repository.guardarCorte(corteDeEjemplo())

        withTimeout(1000) { signal.refrescos.first() }
    }

    @Test
    fun `switching BackendMode in DataStore switches which repository handles observeCortes`(@TempDir tempDir: File) = runTest {
        val cajaDao = mockk<CajaDao>()
        every { cajaDao.observarCortes(any()) } returns flowOf(emptyList())
        val ventaDao = mockk<VentaDao>()
        val retiroDao = mockk<RetiroDao>()
        val appLogger = mockk<AppLogger>()
        val local = LocalCajaRepository(cajaDao, ventaDao, retiroDao, appLogger)

        val api = mockk<CajaApiService>()
        coEvery { api.getCortes(any(), any(), any()) } returns CorteCajaListResponseDto(
            items = emptyList(),
            page = 1,
            pageSize = 50,
            total = 0,
        )
        val remote = RemoteCajaRepository(api, appLogger)

        val preferences = preferences(tempDir)
        val repository = ModeAwareCajaRepository(
            local = local,
            remote = remote,
            preferences = preferences,
            cajaRefreshSignal = CajaRefreshSignal(),
        )

        assertEquals(emptyList<CorteCaja>(), repository.observeCortes("suc-1").first())
        coVerify(exactly = 0) { api.getCortes(any(), any(), any()) }

        preferences.setBackendMode(BackendMode.REMOTO)

        assertEquals(emptyList<CorteCaja>(), repository.observeCortes("suc-1").first())
        coVerify(exactly = 1) { api.getCortes("suc-1", 1, 50) }
    }
}
