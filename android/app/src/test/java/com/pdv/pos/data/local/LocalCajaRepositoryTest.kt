package com.pdv.pos.data.local

import com.pdv.pos.domain.model.CorteCaja
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LocalCajaRepositoryTest {

    private fun ventaEntity(metodoPago: String, total: BigDecimal) = VentaEntity(
        localId = "venta-$metodoPago-$total",
        remoteId = null,
        sucursalId = "suc-1",
        usuarioId = "german",
        folio = "F-001",
        fecha = 1_700_000_000_000L,
        subtotal = total,
        descuento = BigDecimal.ZERO,
        impuestos = BigDecimal.ZERO,
        total = total,
        metodoPago = metodoPago,
        estado = "completada",
        updatedAt = 1_700_000_000_000L,
        isSynced = false,
        deletedAt = null,
    )

    private fun retiroEntity(monto: BigDecimal) = RetiroEfectivoEntity(
        localId = "retiro-$monto",
        remoteId = null,
        sucursalId = "suc-1",
        usuarioId = "german",
        monto = monto,
        motivo = null,
        fecha = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
        isSynced = false,
        deletedAt = null,
    )

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
    fun `calcularTotales sums ventas by metodo de pago and subtracts retiros from monto esperado`() = runTest {
        val cajaDao = mockk<CajaDao>()
        val ventaDao = mockk<VentaDao>()
        val retiroDao = mockk<RetiroDao>()
        val appLogger = mockk<AppLogger>()
        coEvery { ventaDao.getVentasDelPeriodo("suc-1", 0L, 100L) } returns listOf(
            ventaEntity("efectivo", BigDecimal("100.00")),
            ventaEntity("efectivo", BigDecimal("50.00")),
            ventaEntity("tarjeta", BigDecimal("80.00")),
        )
        coEvery { retiroDao.getRetirosDelPeriodo("suc-1", 0L, 100L) } returns listOf(
            retiroEntity(BigDecimal("30.00")),
            retiroEntity(BigDecimal("20.00")),
        )
        val repository = LocalCajaRepository(cajaDao, ventaDao, retiroDao, appLogger)

        val totales = repository.calcularTotales("suc-1", 0L, 100L)

        assertEquals(BigDecimal("230.00"), totales.totalVentas)
        assertEquals(BigDecimal("150.00"), totales.totalEfectivo)
        assertEquals(BigDecimal("80.00"), totales.totalTarjeta)
        assertEquals(BigDecimal("50.00"), totales.totalRetiros)
        assertEquals(BigDecimal("100.00"), totales.montoEsperado)
    }

    @Test
    fun `guardarCorte inserts corte and logs DB_WRITE`() = runTest {
        val cajaDao = mockk<CajaDao>()
        val ventaDao = mockk<VentaDao>()
        val retiroDao = mockk<RetiroDao>()
        val appLogger = mockk<AppLogger>()
        coEvery { cajaDao.insertCorte(any()) } returns Unit
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = LocalCajaRepository(cajaDao, ventaDao, retiroDao, appLogger)

        repository.guardarCorte(corteDeEjemplo())

        coVerify {
            cajaDao.insertCorte(
                match { it.localId == "corte-1" && it.tipo == "parcial" && it.montoEsperado == BigDecimal("150.00") },
            )
        }
        coVerify { appLogger.log(LogType.DB_WRITE, sucursalId = "suc-1", usuario = "german", mensaje = any()) }
    }

    @Test
    fun `guardarCorte propagates a DAO failure without logging DB_WRITE`() = runTest {
        val cajaDao = mockk<CajaDao>()
        val ventaDao = mockk<VentaDao>()
        val retiroDao = mockk<RetiroDao>()
        val appLogger = mockk<AppLogger>()
        coEvery { cajaDao.insertCorte(any()) } throws IllegalStateException("localId duplicado")
        val repository = LocalCajaRepository(cajaDao, ventaDao, retiroDao, appLogger)

        assertFailsWith<IllegalStateException> { repository.guardarCorte(corteDeEjemplo()) }

        coVerify(exactly = 0) { appLogger.log(any(), any(), any(), any()) }
    }
}
