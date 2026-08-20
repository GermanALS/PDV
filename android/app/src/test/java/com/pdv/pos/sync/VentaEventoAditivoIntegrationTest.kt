package com.pdv.pos.sync

import com.pdv.pos.data.local.LocalVentaRepository
import com.pdv.pos.data.local.MovimientoEntity
import com.pdv.pos.data.local.VentaDao
import com.pdv.pos.domain.model.Venta
import com.pdv.pos.domain.model.VentaLinea
import com.pdv.pos.logging.AppLogger
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

// Cierra la validacion de "venta como evento aditivo" diferida desde la
// Parte 6 (docs/PLAN.md Parte 7, "Decisiones abiertas"): en la Parte 6 solo
// se probo EventoAditivoCombiner con BigDecimal sinteticos
// (EventoAditivoCombinerTest); aqui se prueba con el Movimiento real que
// produce LocalVentaRepository.registrarVenta desde dos "dispositivos"
// (dos VentaDao/bases locales independientes) vendiendo el mismo articulo
// de forma concurrente offline.
class VentaEventoAditivoIntegrationTest {

    private fun ventaDeUnaUnidad(id: String, usuarioId: String) = Venta(
        id = id,
        sucursalId = "suc-1",
        usuarioId = usuarioId,
        folio = "F-$id",
        fecha = 1_700_000_000_000L,
        subtotal = BigDecimal("50.00"),
        descuento = BigDecimal.ZERO,
        impuestos = BigDecimal.ZERO,
        total = BigDecimal("50.00"),
        metodoPago = "efectivo",
        estado = "completada",
        lineas = listOf(
            VentaLinea(
                articuloId = "art-1",
                cantidad = BigDecimal("1"),
                precioUnitario = BigDecimal("50.00"),
                subtotal = BigDecimal("50.00"),
            ),
        ),
    )

    private suspend fun registrarVentaYCapturarDeltaDeInventario(usuarioId: String, ventaId: String): BigDecimal {
        val dao = mockk<VentaDao>()
        val movimientos = slot<List<MovimientoEntity>>()
        coEvery { dao.insertVentaCompleta(any(), any(), capture(movimientos), any()) } returns Unit
        val appLogger = mockk<AppLogger>()
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit

        LocalVentaRepository(dao, appLogger).registrarVenta(ventaDeUnaUnidad(id = ventaId, usuarioId = usuarioId))

        val movimiento = movimientos.captured.single()
        check(movimiento.tipo == "salida")
        return movimiento.cantidad.negate()
    }

    @Test
    fun `two devices selling the same articulo concurrently are both applied, not overwritten`() = runTest {
        val deltaDispositivoA = registrarVentaYCapturarDeltaDeInventario(usuarioId = "admin", ventaId = "venta-a")
        val deltaDispositivoB = registrarVentaYCapturarDeltaDeInventario(usuarioId = "user1", ventaId = "venta-b")

        val inventarioResultante = EventoAditivoCombiner.combinar(
            base = BigDecimal("2"),
            deltaLocal = deltaDispositivoA,
            deltaRemoto = deltaDispositivoB,
        )

        assertEquals(BigDecimal("0"), inventarioResultante)
    }
}
