package com.pdv.pos.data.local

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConvertersTest {

    @Test
    fun `modulos permitidos round-trips a plain list`() {
        val modulos = listOf("venta", "inventario", "caja")
        val encoded = Converters.fromModulosPermitidos(modulos)
        assertEquals(modulos, Converters.toModulosPermitidos(encoded))
    }

    @Test
    fun `empty list round-trips to empty list`() {
        assertEquals(emptyList<String>(), Converters.toModulosPermitidos(Converters.fromModulosPermitidos(emptyList())))
    }

    @Test
    fun `a key containing the old comma separator is preserved as one element`() {
        val modulos = listOf("venta,caja", "inventario")
        val encoded = Converters.fromModulosPermitidos(modulos)
        assertEquals(modulos, Converters.toModulosPermitidos(encoded))
    }

    @Test
    fun `bigdecimal round-trips without precision loss`() {
        val value = java.math.BigDecimal("18.5000")
        assertEquals(value, Converters.toBigDecimal(Converters.fromBigDecimal(value)))
    }
}
