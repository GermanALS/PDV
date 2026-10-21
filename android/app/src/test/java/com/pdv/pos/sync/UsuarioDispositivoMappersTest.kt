package com.pdv.pos.sync

import com.pdv.pos.data.local.VentaDetalleEntity
import com.pdv.pos.data.local.VentaEntity
import com.pdv.pos.data.remote.dto.ArticuloCatalogoRequestDto
import com.pdv.pos.data.remote.dto.CorteCajaDto
import com.pdv.pos.data.remote.dto.RetiroEfectivoDto
import com.pdv.pos.data.remote.dto.VentaCreateRequestDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.math.BigDecimal

// PLAN.md Parte 38, M-1: el backend toma usuario_id del JWT; el cliente manda
// su usuario local como usuario_dispositivo y lo recupera en el pull.
class UsuarioDispositivoMappersTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun venta() = VentaEntity(
        localId = "venta-1",
        remoteId = null,
        sucursalId = "suc-1",
        usuarioId = "cajero-local",
        folio = "F-001",
        fecha = 1_700_000_000_000L,
        subtotal = BigDecimal("50"),
        descuento = BigDecimal.ZERO,
        impuestos = BigDecimal.ZERO,
        total = BigDecimal("50"),
        metodoPago = "efectivo",
        estado = "completada",
        updatedAt = 0L,
        isSynced = false,
        deletedAt = null,
    )

    private fun detalle() = VentaDetalleEntity(
        localId = "det-1",
        remoteId = null,
        ventaId = "venta-1",
        articuloId = "art-1",
        cantidad = BigDecimal("1"),
        precioUnitario = BigDecimal("50"),
        subtotal = BigDecimal("50"),
        updatedAt = 0L,
        isSynced = false,
        deletedAt = null,
    )

    private fun corte(usuarioDispositivo: String?) = CorteCajaDto(
        id = "corte-remoto",
        sucursalId = "suc-1",
        usuarioId = "uuid-del-jwt",
        usuarioDispositivo = usuarioDispositivo,
        tipo = "parcial",
        fechaInicio = "2026-08-20T08:00:00Z",
        fechaFin = "2026-08-20T14:00:00Z",
        totalVentas = "10.00",
        totalEfectivo = "10.00",
        totalTarjeta = "0.00",
        totalRetiros = "0.00",
        montoEsperado = "10.00",
        updatedAt = "2026-08-20T14:00:00Z",
    )

    private fun retiro(usuarioDispositivo: String?) = RetiroEfectivoDto(
        id = "retiro-remoto",
        sucursalId = "suc-1",
        usuarioId = "uuid-del-jwt",
        usuarioDispositivo = usuarioDispositivo,
        monto = "5.00",
        fecha = "2026-08-20T11:00:00Z",
        updatedAt = "2026-08-20T11:00:00Z",
    )

    @Test
    fun `venta sends the local user as usuario_dispositivo and no usuario_id`() {
        val body = json.encodeToJsonElement(
            VentaCreateRequestDto.serializer(),
            venta().toCreateRequestDto(listOf(detalle())) { "art-remoto" },
        ).jsonObject

        assertEquals("cajero-local", body.getValue("usuario_dispositivo").jsonPrimitive.content)
        assertFalse("usuario_id" in body)
    }

    @Test
    fun `catalog edit sends no user at all`() {
        val body = json.encodeToJsonElement(
            ArticuloCatalogoRequestDto.serializer(),
            ArticuloCatalogoRequestDto(
                sucursalId = "suc-1",
                nombre = "Articulo",
                unidadMedida = "pieza",
                precioVenta = "10.00",
                editadoEn = "2026-08-20T11:00:00Z",
            ),
        ).jsonObject

        assertFalse("usuario_id" in body)
        assertFalse("usuario_dispositivo" in body)
    }

    @Test
    fun `pulled corte keeps the device user when the backend returns it`() {
        assertEquals("cajero-local", corte("cajero-local").toEntity("corte-1").usuarioId)
    }

    @Test
    fun `pulled corte from before Parte 38 falls back to usuario_id`() {
        assertEquals("uuid-del-jwt", corte(null).toEntity("corte-1").usuarioId)
    }

    @Test
    fun `pulled retiro keeps the device user and falls back to usuario_id`() {
        assertEquals("cajero-local", retiro("cajero-local").toEntity("retiro-1").usuarioId)
        assertEquals("uuid-del-jwt", retiro(null).toEntity("retiro-1").usuarioId)
    }

    @Test
    fun `corte response without usuario_dispositivo still decodes`() {
        val dto = json.decodeFromString(
            CorteCajaDto.serializer(),
            """{"id":"c","sucursal_id":"s","usuario_id":"admin","tipo":"final",
               "fecha_inicio":"2026-08-20T08:00:00Z","fecha_fin":"2026-08-20T14:00:00Z",
               "total_ventas":"0","total_efectivo":"0","total_tarjeta":"0","total_retiros":"0",
               "monto_esperado":"0","updated_at":"2026-08-20T14:00:00Z"}""",
        )

        assertEquals(null, dto.usuarioDispositivo)
    }
}
