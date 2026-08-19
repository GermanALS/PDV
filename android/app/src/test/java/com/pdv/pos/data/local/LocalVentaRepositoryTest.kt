package com.pdv.pos.data.local

import com.pdv.pos.domain.model.Venta
import com.pdv.pos.domain.model.VentaLinea
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertFailsWith

class LocalVentaRepositoryTest {

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
    fun `registrarVenta inserts venta, detalle and movimiento in one transaction and logs DB_WRITE`() = runTest {
        val dao = mockk<VentaDao>()
        val appLogger = mockk<AppLogger>()
        coEvery { dao.insertVentaCompleta(any(), any(), any()) } returns Unit
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = LocalVentaRepository(dao, appLogger)
        val venta = ventaDeEjemplo()

        repository.registrarVenta(venta)

        coVerify {
            dao.insertVentaCompleta(
                venta = match { it.localId == "venta-1" && it.total == BigDecimal("100.00") },
                detalles = match { it.size == 1 && it.single().articuloId == "art-1" },
                movimientos = match {
                    it.size == 1 &&
                        it.single().tipo == "salida" &&
                        it.single().referenciaTipo == "venta" &&
                        it.single().referenciaId == "venta-1" &&
                        it.single().cantidad == BigDecimal("2")
                },
            )
        }
        coVerify { appLogger.log(LogType.DB_WRITE, sucursalId = "suc-1", usuario = "german", mensaje = any()) }
    }

    @Test
    fun `registrarVenta propagates a DAO failure without logging DB_WRITE`() = runTest {
        val dao = mockk<VentaDao>()
        val appLogger = mockk<AppLogger>()
        coEvery { dao.insertVentaCompleta(any(), any(), any()) } throws IllegalStateException("localId duplicado")
        val repository = LocalVentaRepository(dao, appLogger)

        assertFailsWith<IllegalStateException> { repository.registrarVenta(ventaDeEjemplo()) }

        coVerify(exactly = 0) { appLogger.log(any(), any(), any(), any()) }
    }
}
