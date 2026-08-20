package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.CorteCajaCreateRequestDto
import com.pdv.pos.data.remote.dto.CorteCajaDto
import com.pdv.pos.data.remote.dto.TotalesCorteDto
import com.pdv.pos.domain.model.CorteCaja
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RemoteCajaRepositoryTest {

    private fun corteDeEjemplo() = CorteCaja(
        id = "corte-1",
        sucursalId = "suc-1",
        usuarioId = "german",
        tipo = "parcial",
        fechaInicio = 1_700_000_000_000L,
        fechaFin = 1_700_003_600_000L,
        totalVentas = BigDecimal("300.00"),
        totalEfectivo = BigDecimal("200.00"),
        totalTarjeta = BigDecimal("100.00"),
        totalRetiros = BigDecimal("50.00"),
        montoEsperado = BigDecimal("150.00"),
        montoContado = BigDecimal("145.00"),
        diferencia = BigDecimal("-5.00"),
    )

    @Test
    fun `calcularTotales maps the totales DTO to the domain model`() = runTest {
        val api = mockk<CajaApiService>()
        val appLogger = mockk<AppLogger>()
        coEvery { api.getTotales("suc-1", any(), any()) } returns TotalesCorteDto(
            totalVentas = "300.00",
            totalEfectivo = "200.00",
            totalTarjeta = "100.00",
            totalRetiros = "50.00",
            montoEsperado = "150.00",
        )
        val repository = RemoteCajaRepository(api, appLogger)

        val totales = repository.calcularTotales("suc-1", 0L, 100L)

        assertEquals(BigDecimal("300.00"), totales.totalVentas)
        assertEquals(BigDecimal("150.00"), totales.montoEsperado)
        coVerify(exactly = 0) { appLogger.log(any(), any(), any(), any()) }
    }

    @Test
    fun `guardarCorte posts the domain corte mapped to the create request DTO`() = runTest {
        val api = mockk<CajaApiService>()
        val appLogger = mockk<AppLogger>()
        coEvery { api.createCorte(any()) } returns CorteCajaDto(
            id = "remote-1",
            localId = "corte-1",
            sucursalId = "suc-1",
            usuarioId = "german",
            tipo = "parcial",
            fechaInicio = "2023-11-14T22:13:20Z",
            fechaFin = "2023-11-14T23:13:20Z",
            totalVentas = "300.00",
            totalEfectivo = "200.00",
            totalTarjeta = "100.00",
            totalRetiros = "50.00",
            montoEsperado = "150.00",
            montoContado = "145.00",
            diferencia = "-5.00",
            updatedAt = "2023-11-14T23:13:20Z",
        )
        val repository = RemoteCajaRepository(api, appLogger)

        repository.guardarCorte(corteDeEjemplo())

        coVerify {
            api.createCorte(
                CorteCajaCreateRequestDto(
                    localId = "corte-1",
                    sucursalId = "suc-1",
                    usuarioId = "german",
                    tipo = "parcial",
                    fechaInicio = "2023-11-14T22:13:20Z",
                    fechaFin = "2023-11-14T23:13:20Z",
                    totalVentas = "300.00",
                    totalEfectivo = "200.00",
                    totalTarjeta = "100.00",
                    totalRetiros = "50.00",
                    montoEsperado = "150.00",
                    montoContado = "145.00",
                    diferencia = "-5.00",
                ),
            )
        }
        coVerify(exactly = 0) { appLogger.log(any(), any(), any(), any()) }
    }

    @Test
    fun `guardarCorte logs ERROR and rethrows on a network failure`() = runTest {
        val api = mockk<CajaApiService>()
        val appLogger = mockk<AppLogger>()
        coEvery { api.createCorte(any()) } throws IOException("sin conexion")
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = RemoteCajaRepository(api, appLogger)

        assertFailsWith<IOException> { repository.guardarCorte(corteDeEjemplo()) }

        coVerify { appLogger.log(LogType.ERROR, sucursalId = "suc-1", usuario = "german", mensaje = any()) }
    }

    @Test
    fun `guardarCorte logs ERROR and rethrows on a server validation error`() = runTest {
        val api = mockk<CajaApiService>()
        val appLogger = mockk<AppLogger>()
        val error = HttpException(
            Response.error<Any>(422, "".toResponseBody("application/json".toMediaType())),
        )
        coEvery { api.createCorte(any()) } throws error
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = RemoteCajaRepository(api, appLogger)

        assertFailsWith<HttpException> { repository.guardarCorte(corteDeEjemplo()) }

        coVerify { appLogger.log(LogType.ERROR, sucursalId = "suc-1", usuario = "german", mensaje = any()) }
    }
}
