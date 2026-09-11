package com.pdv.pos.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class EventoAditivoCombinerTest {

    @Test
    fun `combines base with both deltas`() {
        val result = EventoAditivoCombiner.combinar(
            base = BigDecimal("10"),
            deltaLocal = BigDecimal("-3"),
            deltaRemoto = BigDecimal("-2"),
        )

        assertEquals(BigDecimal("5"), result)
    }

    @Test
    fun `concurrent decrements from two devices are never lost`() {
        // Ambos dispositivos venden 1 unidad de forma concurrente offline;
        // last-write-wins perderia uno de los dos decrementos.
        val result = EventoAditivoCombiner.combinar(
            base = BigDecimal("2"),
            deltaLocal = BigDecimal("-1"),
            deltaRemoto = BigDecimal("-1"),
        )

        assertEquals(BigDecimal("0"), result)
    }

    @Test
    fun `allows a negative result for later manual review`() {
        val result = EventoAditivoCombiner.combinar(
            base = BigDecimal("1"),
            deltaLocal = BigDecimal("-1"),
            deltaRemoto = BigDecimal("-1"),
        )

        assertEquals(BigDecimal("-1"), result)
    }

    @Test
    fun `combinarConDeteccion flags a negative result`() {
        val result = EventoAditivoCombiner.combinarConDeteccion(
            base = BigDecimal("1"),
            deltaLocal = BigDecimal("-1"),
            deltaRemoto = BigDecimal("-1"),
        )

        assertEquals(BigDecimal("-1"), result.valor)
        assertEquals(true, result.quedoNegativo)
    }

    @Test
    fun `combinarConDeteccion does not flag a zero or positive result`() {
        assertEquals(false, EventoAditivoCombiner.combinarConDeteccion(BigDecimal("2"), BigDecimal("-1"), BigDecimal("-1")).quedoNegativo)
        assertEquals(false, EventoAditivoCombiner.combinarConDeteccion(BigDecimal("5"), BigDecimal("-1"), BigDecimal("0")).quedoNegativo)
    }
}
