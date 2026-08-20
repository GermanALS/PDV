package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.RetiroEfectivoCreateRequestDto
import com.pdv.pos.data.remote.dto.RetiroEfectivoDto
import com.pdv.pos.domain.model.RetiroEfectivo
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

class RemoteRetiroEfectivoRepositoryTest {

    private fun retiroDeEjemplo() = RetiroEfectivo(
        id = "retiro-1",
        sucursalId = "suc-1",
        usuarioId = "german",
        monto = BigDecimal("100.00"),
        motivo = "Pago a proveedor",
        fecha = 1_700_000_000_000L,
    )

    @Test
    fun `registrarRetiro posts the domain retiro mapped to the create request DTO`() = runTest {
        val api = mockk<RetiroApiService>()
        val appLogger = mockk<AppLogger>()
        coEvery { api.createRetiro(any()) } returns RetiroEfectivoDto(
            id = "remote-1",
            localId = "retiro-1",
            sucursalId = "suc-1",
            usuarioId = "german",
            monto = "100.00",
            motivo = "Pago a proveedor",
            fecha = "2023-11-14T22:13:20Z",
            updatedAt = "2023-11-14T22:13:20Z",
        )
        val repository = RemoteRetiroEfectivoRepository(api, appLogger)

        repository.registrarRetiro(retiroDeEjemplo())

        coVerify {
            api.createRetiro(
                RetiroEfectivoCreateRequestDto(
                    localId = "retiro-1",
                    sucursalId = "suc-1",
                    usuarioId = "german",
                    monto = "100.00",
                    motivo = "Pago a proveedor",
                    fecha = "2023-11-14T22:13:20Z",
                ),
            )
        }
        coVerify(exactly = 0) { appLogger.log(any(), any(), any(), any()) }
    }

    @Test
    fun `registrarRetiro logs ERROR and rethrows on a network failure`() = runTest {
        val api = mockk<RetiroApiService>()
        val appLogger = mockk<AppLogger>()
        coEvery { api.createRetiro(any()) } throws IOException("sin conexion")
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = RemoteRetiroEfectivoRepository(api, appLogger)

        assertFailsWith<IOException> { repository.registrarRetiro(retiroDeEjemplo()) }

        coVerify { appLogger.log(LogType.ERROR, sucursalId = "suc-1", usuario = "german", mensaje = any()) }
    }

    @Test
    fun `registrarRetiro logs ERROR and rethrows on a server validation error`() = runTest {
        val api = mockk<RetiroApiService>()
        val appLogger = mockk<AppLogger>()
        val error = HttpException(
            Response.error<Any>(422, "".toResponseBody("application/json".toMediaType())),
        )
        coEvery { api.createRetiro(any()) } throws error
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = RemoteRetiroEfectivoRepository(api, appLogger)

        assertFailsWith<HttpException> { repository.registrarRetiro(retiroDeEjemplo()) }

        coVerify { appLogger.log(LogType.ERROR, sucursalId = "suc-1", usuario = "german", mensaje = any()) }
    }
}
