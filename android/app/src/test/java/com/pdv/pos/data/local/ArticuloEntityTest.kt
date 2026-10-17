package com.pdv.pos.data.local

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ArticuloEntityTest {

    private fun articulo() = ArticuloEntity(
        localId = "art-1", remoteId = "r-1", codigoBarras = null, sku = "SKU-1", nombre = "Cafe",
        descripcion = null, categoria = "Abarrotes", unidadMedida = "pieza", precioVenta = BigDecimal("19.00"),
        costo = BigDecimal("12.00"), activo = true, updatedAt = 1L, isSynced = true, deletedAt = null,
    )

    @Test
    fun `catalogoDifiere ignores tracking fields and the scale of decimals`() {
        val otro = articulo().copy(
            precioVenta = BigDecimal("19.0"), costo = BigDecimal("12"), updatedAt = 99L, isSynced = false,
        )

        assertFalse(articulo().catalogoDifiere(otro))
    }

    @Test
    fun `catalogoDifiere detects a changed attribute`() {
        assertTrue(articulo().catalogoDifiere(articulo().copy(nombre = "Cafe molido")))
        assertTrue(articulo().catalogoDifiere(articulo().copy(precioVenta = BigDecimal("19.50"))))
        assertTrue(articulo().catalogoDifiere(articulo().copy(categoria = null)))
    }

    @Test
    fun `catalogoDifiere detects a cost added or removed`() {
        assertTrue(articulo().catalogoDifiere(articulo().copy(costo = null)))
        assertTrue(articulo().copy(costo = null).catalogoDifiere(articulo()))
        assertFalse(articulo().copy(costo = null).catalogoDifiere(articulo().copy(costo = null)))
    }
}
