package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.VentaCreateRequestDto
import com.pdv.pos.data.remote.dto.VentaDetalleCreateRequestDto
import com.pdv.pos.data.remote.dto.VentaDetalleDto
import com.pdv.pos.data.remote.dto.VentaDto
import com.pdv.pos.domain.model.Venta
import com.pdv.pos.domain.model.VentaLinea
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
import kotlin.test.assertFailsWith

class RemoteVentaRepositoryTest {

    private fun ventaDeEjemplo() = Venta(
        id = "venta-1",
        sucursalId = "suc-1",
        usuarioId = "german",
        folio = "F-001",
        fecha = 1_700_000_000_000L,
        subtotal = BigDecimal("100.00"),
        descuento = BigDecimal.ZERO,
        impuestos = BigDecimal.ZERO,
        total = BigDecimal("100.00"),
        metodoPago = "efectivo",
        estado = "completada",
        lineas = listOf(
            VentaLinea(
                articuloId = "art-1",
                cantidad = BigDecimal("2"),
                precioUnitario = BigDecimal("50.00"),
                subtotal = BigDecimal("100.00"),
            ),
        ),
    )

    @Test
    fun `registrarVenta posts the domain venta mapped to the create request DTO`() = runTest {
        val api = mockk<VentaApiService>()
        val appLogger = mockk<AppLogger>()
        coEvery { api.createVenta(any()) } returns VentaDto(
            id = "remote-1",
            localId = "venta-1",
            sucursalId = "suc-1",
            usuarioId = "german",
            folio = "F-001",
            fecha = "2023-11-14T22:13:20Z",
            subtotal = "100.00",
            descuento = "0",
            impuestos = "0",
            total = "100.00",
            metodoPago = "efectivo",
            estado = "completada",
            updatedAt = "2023-11-14T22:13:20Z",
            lineas = listOf(
                VentaDetalleDto(
                    id = "det-1",
                    articuloId = "art-1",
                    cantidad = "2",
                    precioUnitario = "50.00",
                    subtotal = "100.00",
                ),
            ),
        )
        val repository = RemoteVentaRepository(api, appLogger)

        repository.registrarVenta(ventaDeEjemplo())

        coVerify {
            api.createVenta(
                VentaCreateRequestDto(
                    localId = "venta-1",
                    sucursalId = "suc-1",
                    usuarioId = "german",
                    folio = "F-001",
                    fecha = "2023-11-14T22:13:20Z",
                    subtotal = "100.00",
                    descuento = "0",
                    impuestos = "0",
                    total = "100.00",
                    metodoPago = "efectivo",
                    estado = "completada",
                    lineas = listOf(
                        VentaDetalleCreateRequestDto(
                            articuloId = "art-1",
                            cantidad = "2",
                            precioUnitario = "50.00",
                            subtotal = "100.00",
                        ),
                    ),
                ),
            )
        }
        coVerify(exactly = 0) { appLogger.log(any(), any(), any(), any()) }
    }

    @Test
    fun `registrarVenta logs ERROR and rethrows on a network failure`() = runTest {
        val api = mockk<VentaApiService>()
        val appLogger = mockk<AppLogger>()
        coEvery { api.createVenta(any()) } throws IOException("sin conexion")
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = RemoteVentaRepository(api, appLogger)

        assertFailsWith<IOException> { repository.registrarVenta(ventaDeEjemplo()) }

        coVerify { appLogger.log(LogType.ERROR, sucursalId = "suc-1", usuario = "german", mensaje = any()) }
    }

    @Test
    fun `registrarVenta logs ERROR and rethrows on a server validation error`() = runTest {
        val api = mockk<VentaApiService>()
        val appLogger = mockk<AppLogger>()
        val error = HttpException(
            Response.error<Any>(422, "".toResponseBody("application/json".toMediaType())),
        )
        coEvery { api.createVenta(any()) } throws error
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = RemoteVentaRepository(api, appLogger)

        assertFailsWith<HttpException> { repository.registrarVenta(ventaDeEjemplo()) }

        coVerify { appLogger.log(LogType.ERROR, sucursalId = "suc-1", usuario = "german", mensaje = any()) }
    }
}
