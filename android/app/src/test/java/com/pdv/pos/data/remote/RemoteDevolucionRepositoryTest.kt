package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.DevolucionCreateRequestDto
import com.pdv.pos.data.remote.dto.DevolucionDetalleCreateRequestDto
import com.pdv.pos.data.remote.dto.DevolucionDetalleDto
import com.pdv.pos.data.remote.dto.DevolucionDto
import com.pdv.pos.domain.model.Devolucion
import com.pdv.pos.domain.model.DevolucionLinea
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

class RemoteDevolucionRepositoryTest {

    private fun devolucionDeEjemplo() = Devolucion(
        id = "devolucion-1",
        sucursalId = "suc-1",
        usuarioId = "german",
        ventaId = "venta-1",
        folio = "D-001",
        fecha = 1_700_000_000_000L,
        estado = "registrada",
        lineas = listOf(
            DevolucionLinea(
                articuloId = "art-1",
                cantidad = BigDecimal("1"),
                motivo = "Producto dañado",
                condicion = "defectuoso",
            ),
        ),
    )

    @Test
    fun `registrarDevolucion posts the domain devolucion mapped to the create request DTO`() = runTest {
        val api = mockk<DevolucionApiService>()
        val appLogger = mockk<AppLogger>()
        coEvery { api.createDevolucion(any()) } returns DevolucionDto(
            id = "remote-1",
            localId = "devolucion-1",
            sucursalId = "suc-1",
            usuarioId = "german",
            ventaId = "venta-1",
            folio = "D-001",
            fecha = "2023-11-14T22:13:20Z",
            estado = "registrada",
            updatedAt = "2023-11-14T22:13:20Z",
            lineas = listOf(
                DevolucionDetalleDto(
                    id = "det-1",
                    articuloId = "art-1",
                    cantidad = "1",
                    motivo = "Producto dañado",
                    condicion = "defectuoso",
                ),
            ),
        )
        val repository = RemoteDevolucionRepository(api, appLogger)

        repository.registrarDevolucion(devolucionDeEjemplo())

        coVerify {
            api.createDevolucion(
                DevolucionCreateRequestDto(
                    localId = "devolucion-1",
                    sucursalId = "suc-1",
                    usuarioId = "german",
                    ventaId = "venta-1",
                    folio = "D-001",
                    fecha = "2023-11-14T22:13:20Z",
                    estado = "registrada",
                    lineas = listOf(
                        DevolucionDetalleCreateRequestDto(
                            articuloId = "art-1",
                            cantidad = "1",
                            motivo = "Producto dañado",
                            condicion = "defectuoso",
                        ),
                    ),
                ),
            )
        }
        coVerify(exactly = 0) { appLogger.log(any(), any(), any(), any()) }
    }

    @Test
    fun `registrarDevolucion logs ERROR and rethrows on a network failure`() = runTest {
        val api = mockk<DevolucionApiService>()
        val appLogger = mockk<AppLogger>()
        coEvery { api.createDevolucion(any()) } throws IOException("sin conexion")
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = RemoteDevolucionRepository(api, appLogger)

        assertFailsWith<IOException> { repository.registrarDevolucion(devolucionDeEjemplo()) }

        coVerify { appLogger.log(LogType.ERROR, sucursalId = "suc-1", usuario = "german", mensaje = any()) }
    }

    @Test
    fun `registrarDevolucion logs ERROR and rethrows on a server validation error`() = runTest {
        val api = mockk<DevolucionApiService>()
        val appLogger = mockk<AppLogger>()
        val error = HttpException(
            Response.error<Any>(422, "".toResponseBody("application/json".toMediaType())),
        )
        coEvery { api.createDevolucion(any()) } throws error
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = RemoteDevolucionRepository(api, appLogger)

        assertFailsWith<HttpException> { repository.registrarDevolucion(devolucionDeEjemplo()) }

        coVerify { appLogger.log(LogType.ERROR, sucursalId = "suc-1", usuario = "german", mensaje = any()) }
    }
}
