package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.AjusteInventarioDto
import com.pdv.pos.data.remote.dto.ArticuloDto
import com.pdv.pos.data.remote.dto.ArticuloEdicionRequestDto
import com.pdv.pos.data.remote.dto.InventarioDto
import com.pdv.pos.data.remote.dto.InventarioItemDto
import com.pdv.pos.data.remote.dto.InventarioListResponseDto
import com.pdv.pos.data.remote.dto.ValoresDto
import com.pdv.pos.domain.model.EdicionArticulo
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.math.BigDecimal
import kotlin.test.assertFailsWith

class RemoteInventarioRepositoryTest {

    private fun edicionDeEjemplo() = EdicionArticulo(
        articuloId = "art-1",
        sucursalId = "suc-1",
        usuarioId = "german",
        nombre = "Refresco de cola 600ml",
        descripcion = null,
        categoria = "Bebidas",
        unidadMedida = "pieza",
        precioVenta = BigDecimal("19.00"),
        costo = BigDecimal("12.00"),
        nuevaCantidad = BigDecimal("30"),
        ubicacion = "Estante A1",
    )

    @Test
    fun `observarInventario maps the paginated response to domain`() = runTest {
        val api = mockk<InventarioApiService>()
        val appLogger = mockk<AppLogger>()
        coEvery { api.getInventario(sucursalId = "suc-1", q = null, page = 1, pageSize = 20) } returns
            InventarioListResponseDto(
                items = listOf(
                    InventarioItemDto(
                        articuloId = "art-1",
                        sku = "REF-001",
                        nombre = "Refresco de cola 600ml",
                        unidadMedida = "pieza",
                        precioVenta = "18.50",
                        cantidad = "24.000",
                        ubicacion = "Estante A1",
                    ),
                ),
                page = 1,
                pageSize = 20,
                total = 1,
            )
        val repository = RemoteInventarioRepository(api, appLogger)

        val pagina = repository.observarInventario(sucursalId = "suc-1", busqueda = "", pagina = 1, tamanioPagina = 20).first()

        assertEquals(1, pagina.total)
        assertEquals("REF-001", pagina.items.single().articulo.sku)
        assertEquals(BigDecimal("24.000"), pagina.items.single().cantidad)
    }

    @Test
    fun `observarCategorias returns the values from the api`() = runTest {
        val api = mockk<InventarioApiService>()
        val appLogger = mockk<AppLogger>()
        coEvery { api.getCategorias() } returns ValoresDto(valores = listOf("Bebidas", "Abarrotes"))
        val repository = RemoteInventarioRepository(api, appLogger)

        val categorias = repository.observarCategorias().first()

        assertEquals(listOf("Bebidas", "Abarrotes"), categorias)
    }

    @Test
    fun `actualizarArticulo posts the edicion mapped to the request dto and does not log`() = runTest {
        val api = mockk<InventarioApiService>()
        val appLogger = mockk<AppLogger>()
        coEvery { api.ajustarArticulo(any(), any()) } returns AjusteInventarioDto(
            articulo = ArticuloDto(
                id = "art-1",
                sku = "REF-001",
                nombre = "Refresco de cola 600ml",
                unidadMedida = "pieza",
                precioVenta = "19.00",
                updatedAt = "2023-11-14T22:13:20Z",
            ),
            inventario = InventarioDto(
                id = "inv-1",
                sucursalId = "suc-1",
                articuloId = "art-1",
                cantidad = "30.000",
                updatedAt = "2023-11-14T22:13:20Z",
            ),
        )
        val repository = RemoteInventarioRepository(api, appLogger)

        repository.actualizarArticulo(edicionDeEjemplo())

        coVerify {
            api.ajustarArticulo(
                "art-1",
                ArticuloEdicionRequestDto(
                    sucursalId = "suc-1",
                    usuarioId = "german",
                    nombre = "Refresco de cola 600ml",
                    descripcion = null,
                    categoria = "Bebidas",
                    unidadMedida = "pieza",
                    precioVenta = "19.00",
                    costo = "12.00",
                    cantidad = "30",
                    ubicacion = "Estante A1",
                ),
            )
        }
        coVerify(exactly = 0) { appLogger.log(any(), any(), any(), any()) }
    }

    @Test
    fun `actualizarArticulo logs ERROR and rethrows on a network failure`() = runTest {
        val api = mockk<InventarioApiService>()
        val appLogger = mockk<AppLogger>()
        coEvery { api.ajustarArticulo(any(), any()) } throws IOException("sin conexion")
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = RemoteInventarioRepository(api, appLogger)

        assertFailsWith<IOException> { repository.actualizarArticulo(edicionDeEjemplo()) }

        coVerify { appLogger.log(LogType.ERROR, sucursalId = "suc-1", usuario = "german", mensaje = any()) }
    }

    @Test
    fun `actualizarArticulo logs ERROR and rethrows on a server error`() = runTest {
        val api = mockk<InventarioApiService>()
        val appLogger = mockk<AppLogger>()
        val error = HttpException(Response.error<Any>(404, "".toResponseBody("application/json".toMediaType())))
        coEvery { api.ajustarArticulo(any(), any()) } throws error
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = RemoteInventarioRepository(api, appLogger)

        assertFailsWith<HttpException> { repository.actualizarArticulo(edicionDeEjemplo()) }

        coVerify { appLogger.log(LogType.ERROR, sucursalId = "suc-1", usuario = "german", mensaje = any()) }
    }
}
