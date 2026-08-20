package com.pdv.pos.venta.ticket

import com.pdv.pos.domain.model.Articulo
import com.pdv.pos.venta.LineaCarrito
import com.pdv.pos.venta.MetodoPago
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class TicketFormatterTest {

    private val articulo = Articulo(
        id = "11111111-1111-4111-8111-111111111111",
        codigoBarras = "7501234567890",
        sku = "REF-001",
        nombre = "Refresco de cola 600ml",
        unidadMedida = "pieza",
        precioVenta = BigDecimal("18.50"),
    )
    private val carrito = listOf(LineaCarrito(articulo = articulo, cantidad = 2))

    @Test
    fun `generarLineas incluye encabezado, articulos, totales y cambio para una venta en efectivo`() {
        val lineas = TicketFormatter.generarLineas(
            folio = "V-123",
            fecha = 0L,
            sucursalNombre = "Sucursal Centro",
            sucursalDireccion = "Av. Siempre Viva 123",
            usuarioId = "admin",
            carrito = carrito,
            subtotal = BigDecimal("37.00"),
            descuento = BigDecimal.ZERO,
            impuestos = BigDecimal.ZERO,
            total = BigDecimal("37.00"),
            metodoPago = MetodoPago.EFECTIVO,
            efectivoRecibido = BigDecimal("50.00"),
            cambio = BigDecimal("13.00"),
        )

        assertTrue(lineas.any { it == "Sucursal Centro" })
        assertTrue(lineas.any { it == "Av. Siempre Viva 123" })
        assertTrue(lineas.any { it == "Folio: V-123" })
        assertTrue(lineas.any { it.contains("Refresco de cola 600ml") })
        assertTrue(lineas.any { it == "Total: 37.00" })
        assertTrue(lineas.any { it == "Efectivo recibido: 50.00" })
        assertTrue(lineas.any { it == "Cambio: 13.00" })
    }

    @Test
    fun `generarLineas no incluye efectivo ni cambio para una venta con tarjeta`() {
        val lineas = TicketFormatter.generarLineas(
            folio = "V-124",
            fecha = 0L,
            sucursalNombre = "Sucursal Centro",
            sucursalDireccion = null,
            usuarioId = "admin",
            carrito = carrito,
            subtotal = BigDecimal("37.00"),
            descuento = BigDecimal.ZERO,
            impuestos = BigDecimal.ZERO,
            total = BigDecimal("37.00"),
            metodoPago = MetodoPago.TARJETA,
            efectivoRecibido = null,
            cambio = null,
        )

        assertFalse(lineas.any { it.startsWith("Efectivo recibido") })
        assertFalse(lineas.any { it.startsWith("Cambio") })
        assertTrue(lineas.any { it == "Metodo de pago: Tarjeta" })
    }
}
