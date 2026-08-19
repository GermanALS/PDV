package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.ArticuloDto
import com.pdv.pos.data.remote.dto.ArticuloNuevoRequestDto
import com.pdv.pos.data.remote.dto.EntradaCreateRequestDto
import com.pdv.pos.data.remote.dto.EntradaDto
import com.pdv.pos.data.remote.dto.InventarioDto
import com.pdv.pos.data.remote.dto.MovimientoDto
import com.pdv.pos.domain.model.ArticuloNuevo
import com.pdv.pos.domain.model.Entrada
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

class RemoteEntradaRepositoryTest {

    private fun entradaArticuloExistente() = Entrada.DeArticuloExistente(
        id = "entrada-1",
        sucursalId = "suc-1",
        usuarioId = "german",
        fecha = 1_700_000_000_000L,
        cantidad = BigDecimal("5"),
        ubicacion = "Estante A1",
        articuloId = "art-1",
    )

    private fun entradaArticuloNuevo() = Entrada.DeArticuloNuevo(
        id = "entrada-2",
        sucursalId = "suc-1",
        usuarioId = "german",
        fecha = 1_700_000_000_000L,
        cantidad = BigDecimal("10"),
        ubicacion = "Estante B2",
        articulo = ArticuloNuevo(
            id = "art-nuevo-1",
            codigoBarras = null,
            sku = "NEW-001",
            nombre = "Articulo nuevo",
            descripcion = null,
            categoria = null,
            unidadMedida = "pieza",
            precioVenta = BigDecimal("15.00"),
            costo = null,
        ),
    )

    private fun entradaDtoDeEjemplo() = EntradaDto(
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
        articulo = null,
    )

    @Test
    fun `registrarEntrada de articulo existente posts articuloId without articuloNuevo`() = runTest {
        val api = mockk<EntradaApiService>()
        val appLogger = mockk<AppLogger>()
        coEvery { api.createEntrada(any()) } returns entradaDtoDeEjemplo()
        val repository = RemoteEntradaRepository(api, appLogger)

        repository.registrarEntrada(entradaArticuloExistente())

        coVerify {
            api.createEntrada(
                EntradaCreateRequestDto(
                    localId = "entrada-1",
                    sucursalId = "suc-1",
                    usuarioId = "german",
                    fecha = "2023-11-14T22:13:20Z",
                    cantidad = "5",
                    ubicacion = "Estante A1",
                    articuloId = "art-1",
                    articuloNuevo = null,
                ),
            )
        }
        coVerify(exactly = 0) { appLogger.log(any(), any(), any(), any()) }
    }

    @Test
    fun `registrarEntrada de articulo nuevo posts articuloNuevo without articuloId`() = runTest {
        val api = mockk<EntradaApiService>()
        val appLogger = mockk<AppLogger>()
        coEvery { api.createEntrada(any()) } returns entradaDtoDeEjemplo().copy(
            articulo = ArticuloDto(
                id = "art-remote-1",
                sku = "NEW-001",
                nombre = "Articulo nuevo",
                unidadMedida = "pieza",
                precioVenta = "15.00",
                updatedAt = "2023-11-14T22:13:20Z",
            ),
        )
        val repository = RemoteEntradaRepository(api, appLogger)

        repository.registrarEntrada(entradaArticuloNuevo())

        coVerify {
            api.createEntrada(
                EntradaCreateRequestDto(
                    localId = "entrada-2",
                    sucursalId = "suc-1",
                    usuarioId = "german",
                    fecha = "2023-11-14T22:13:20Z",
                    cantidad = "10",
                    ubicacion = "Estante B2",
                    articuloId = null,
                    articuloNuevo = ArticuloNuevoRequestDto(
                        localId = "art-nuevo-1",
                        codigoBarras = null,
                        sku = "NEW-001",
                        nombre = "Articulo nuevo",
                        descripcion = null,
                        categoria = null,
                        unidadMedida = "pieza",
                        precioVenta = "15.00",
                        costo = null,
                    ),
                ),
            )
        }
    }

    @Test
    fun `registrarEntrada logs ERROR and rethrows on a network failure`() = runTest {
        val api = mockk<EntradaApiService>()
        val appLogger = mockk<AppLogger>()
        coEvery { api.createEntrada(any()) } throws IOException("sin conexion")
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = RemoteEntradaRepository(api, appLogger)

        assertFailsWith<IOException> { repository.registrarEntrada(entradaArticuloExistente()) }

        coVerify { appLogger.log(LogType.ERROR, sucursalId = "suc-1", usuario = "german", mensaje = any()) }
    }

    @Test
    fun `registrarEntrada logs ERROR and rethrows on a server validation error`() = runTest {
        val api = mockk<EntradaApiService>()
        val appLogger = mockk<AppLogger>()
        val error = HttpException(
            Response.error<Any>(422, "".toResponseBody("application/json".toMediaType())),
        )
        coEvery { api.createEntrada(any()) } throws error
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = RemoteEntradaRepository(api, appLogger)

        assertFailsWith<HttpException> { repository.registrarEntrada(entradaArticuloExistente()) }

        coVerify { appLogger.log(LogType.ERROR, sucursalId = "suc-1", usuario = "german", mensaje = any()) }
    }
}
