package com.pdv.pos.data.local

import com.pdv.pos.domain.model.ArticuloNuevo
import com.pdv.pos.domain.model.Entrada
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertFailsWith

class LocalEntradaRepositoryTest {

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
            codigoBarras = "7501234567999",
            sku = "NEW-001",
            nombre = "Articulo nuevo de prueba",
            descripcion = null,
            categoria = null,
            unidadMedida = "pieza",
            precioVenta = BigDecimal("15.00"),
            costo = BigDecimal("9.00"),
        ),
    )

    @Test
    fun `registrarEntrada de articulo existente upserts inventario and movimiento and logs DB_WRITE`() = runTest {
        val dao = mockk<EntradaDao>()
        val appLogger = mockk<AppLogger>()
        coEvery { dao.insertEntradaCompleta(any(), any(), any(), any(), any(), any(), any()) } returns Unit
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = LocalEntradaRepository(dao, appLogger)

        repository.registrarEntrada(entradaArticuloExistente())

        coVerify {
            dao.insertEntradaCompleta(
                articuloNuevo = null,
                sucursalId = "suc-1",
                articuloId = "art-1",
                deltaCantidad = BigDecimal("5"),
                ubicacion = "Estante A1",
                movimiento = match {
                    it.tipo == "entrada" &&
                        it.referenciaTipo == "entrada_manual" &&
                        it.referenciaId == "entrada-1" &&
                        it.usuarioId == "german" &&
                        it.cantidad == BigDecimal("5")
                },
                now = any(),
            )
        }
        coVerify { appLogger.log(LogType.DB_WRITE, sucursalId = "suc-1", usuario = "german", mensaje = any()) }
    }

    @Test
    fun `registrarEntrada de articulo nuevo also inserts the articulo entity`() = runTest {
        val dao = mockk<EntradaDao>()
        val appLogger = mockk<AppLogger>()
        coEvery { dao.insertEntradaCompleta(any(), any(), any(), any(), any(), any(), any()) } returns Unit
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = LocalEntradaRepository(dao, appLogger)

        repository.registrarEntrada(entradaArticuloNuevo())

        coVerify {
            dao.insertEntradaCompleta(
                articuloNuevo = match { it.localId == "art-nuevo-1" && it.sku == "NEW-001" },
                sucursalId = "suc-1",
                articuloId = "art-nuevo-1",
                deltaCantidad = BigDecimal("10"),
                ubicacion = "Estante B2",
                movimiento = any(),
                now = any(),
            )
        }
    }

    @Test
    fun `registrarEntrada propagates a DAO failure without logging DB_WRITE`() = runTest {
        val dao = mockk<EntradaDao>()
        val appLogger = mockk<AppLogger>()
        coEvery {
            dao.insertEntradaCompleta(any(), any(), any(), any(), any(), any(), any())
        } throws IllegalStateException("localId duplicado")
        val repository = LocalEntradaRepository(dao, appLogger)

        assertFailsWith<IllegalStateException> { repository.registrarEntrada(entradaArticuloExistente()) }

        coVerify(exactly = 0) { appLogger.log(any(), any(), any(), any()) }
    }
}
