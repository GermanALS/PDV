package com.pdv.pos.sync

import com.pdv.pos.data.local.ArticuloEntity
import com.pdv.pos.data.local.MovimientoEntity
import com.pdv.pos.data.local.VentaDetalleEntity
import com.pdv.pos.data.local.VentaEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class SyncMappersTest {

    private fun venta() = VentaEntity(
        localId = "venta-1",
        remoteId = null,
        sucursalId = "suc-1",
        usuarioId = "u-1",
        folio = "F-001",
        fecha = 1_700_000_000_000L,
        subtotal = BigDecimal("100.5"),
        descuento = BigDecimal.ZERO,
        impuestos = BigDecimal.ZERO,
        total = BigDecimal("100.5"),
        metodoPago = "efectivo",
        estado = "completada",
        updatedAt = 1_700_000_000_000L,
        isSynced = false,
        deletedAt = null,
    )

    private fun detalle() = VentaDetalleEntity(
        localId = "det-1",
        remoteId = null,
        ventaId = "venta-1",
        articuloId = "art-1",
        cantidad = BigDecimal("2"),
        precioUnitario = BigDecimal("50.25"),
        subtotal = BigDecimal("100.5"),
        updatedAt = 0L,
        isSynced = false,
        deletedAt = null,
    )

    private fun movimientoEntrada() = MovimientoEntity(
        localId = "mov-1",
        remoteId = null,
        sucursalId = "suc-1",
        articuloId = "art-1",
        usuarioId = "u-1",
        tipo = "entrada",
        cantidad = BigDecimal("10"),
        ubicacion = "A1",
        referenciaTipo = "entrada_manual",
        referenciaId = "entrada-1",
        fecha = 1_700_000_000_000L,
        updatedAt = 0L,
        isSynced = false,
        deletedAt = null,
    )

    private fun articulo() = ArticuloEntity(
        localId = "art-1",
        remoteId = null,
        codigoBarras = "111",
        sku = "SKU-1",
        nombre = "Cafe",
        descripcion = null,
        categoria = "bebidas",
        unidadMedida = "unidad",
        precioVenta = BigDecimal("30"),
        costo = BigDecimal("18"),
        activo = true,
        updatedAt = 0L,
        isSynced = false,
        deletedAt = null,
    )

    @Test
    fun `venta maps epoch millis to ISO-8601 and BigDecimal to plain string`() {
        val dto = venta().toCreateRequestDto(listOf(detalle())) { "art-remoto-1" }

        assertEquals("venta-1", dto.localId)
        assertEquals("2023-11-14T22:13:20Z", dto.fecha)
        assertEquals("100.5", dto.total)
        assertEquals(1, dto.lineas.size)
        assertEquals("50.25", dto.lineas.first().precioUnitario)
        assertEquals("det-1", dto.lineas.first().localId)
    }

    @Test
    fun `venta resolves each linea's articulo_id to its remote id, not the local one`() {
        val dto = venta().toCreateRequestDto(listOf(detalle())) { local -> "$local-remoto" }

        assertEquals("art-1-remoto", dto.lineas.first().articuloId)
    }

    @Test
    fun `entrada uses the movimiento referenciaId as the backend local_id`() {
        val dto = movimientoEntrada().toEntradaCreateRequestDto(articuloIdRemoto = null, articuloNuevo = null)

        assertEquals("entrada-1", dto.localId)
        assertEquals("art-1", dto.articuloId)
        assertNull(dto.articuloNuevo)
    }

    @Test
    fun `entrada with a synced articulo sends the remote articulo id, not the local one`() {
        val dto = movimientoEntrada().toEntradaCreateRequestDto(articuloIdRemoto = "art-remoto-9", articuloNuevo = null)

        assertEquals("art-remoto-9", dto.articuloId)
        assertNull(dto.articuloNuevo)
    }

    @Test
    fun `entrada with a new articulo sends articulo_nuevo and no articulo_id`() {
        val dto = movimientoEntrada().toEntradaCreateRequestDto(articuloIdRemoto = null, articuloNuevo = articulo())

        assertNull(dto.articuloId)
        assertEquals("SKU-1", dto.articuloNuevo?.sku)
        assertEquals("art-1", dto.articuloNuevo?.localId)
    }
}
