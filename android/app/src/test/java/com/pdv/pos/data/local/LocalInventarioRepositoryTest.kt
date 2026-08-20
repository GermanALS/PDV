package com.pdv.pos.data.local

import com.pdv.pos.domain.model.EdicionArticulo
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertFailsWith

class LocalInventarioRepositoryTest {

    private fun articuloEntity() = ArticuloEntity(
        localId = "art-1",
        remoteId = null,
        codigoBarras = "7501234567890",
        sku = "REF-001",
        nombre = "Refresco de cola 600ml",
        descripcion = null,
        categoria = "Bebidas",
        unidadMedida = "pieza",
        precioVenta = BigDecimal("18.50"),
        costo = BigDecimal("12.00"),
        activo = true,
        updatedAt = 1_700_000_000_000L,
        isSynced = true,
        deletedAt = null,
    )

    private fun edicionDeEjemplo() = EdicionArticulo(
        articuloId = "art-1",
        sucursalId = "suc-1",
        usuarioId = "german",
        nombre = "Refresco de cola 600ml",
        descripcion = "Edicion especial",
        categoria = "Bebidas",
        unidadMedida = "pieza",
        precioVenta = BigDecimal("19.00"),
        costo = BigDecimal("12.00"),
        nuevaCantidad = BigDecimal("30"),
        ubicacion = "Estante A1",
    )

    @Test
    fun `actualizarArticulo updates catalog attributes and quantity then logs DB_WRITE`() = runTest {
        val dao = mockk<InventarioDao>()
        val appLogger = mockk<AppLogger>()
        coEvery { dao.getArticulo("art-1") } returns articuloEntity()
        coEvery { dao.actualizarArticuloCompleto(any(), any(), any(), any(), any(), any()) } returns Unit
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = LocalInventarioRepository(dao, appLogger)

        repository.actualizarArticulo(edicionDeEjemplo())

        coVerify {
            dao.actualizarArticuloCompleto(
                articulo = match {
                    it.localId == "art-1" && it.precioVenta == BigDecimal("19.00") && it.descripcion == "Edicion especial"
                },
                sucursalId = "suc-1",
                usuarioId = "german",
                nuevaCantidad = BigDecimal("30"),
                ubicacion = "Estante A1",
                now = any(),
            )
        }
        coVerify { appLogger.log(LogType.DB_WRITE, sucursalId = "suc-1", usuario = "german", mensaje = any()) }
    }

    @Test
    fun `actualizarArticulo propagates a DAO failure without logging DB_WRITE`() = runTest {
        val dao = mockk<InventarioDao>()
        val appLogger = mockk<AppLogger>()
        coEvery { dao.getArticulo("art-1") } returns articuloEntity()
        coEvery {
            dao.actualizarArticuloCompleto(any(), any(), any(), any(), any(), any())
        } throws IllegalStateException("localId duplicado")
        val repository = LocalInventarioRepository(dao, appLogger)

        assertFailsWith<IllegalStateException> { repository.actualizarArticulo(edicionDeEjemplo()) }

        coVerify(exactly = 0) { appLogger.log(any(), any(), any(), any()) }
    }
}
