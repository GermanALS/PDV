package com.pdv.pos.caja.export

import com.pdv.pos.domain.model.CorteCaja
import com.pdv.pos.domain.model.RetiroEfectivo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.ZoneOffset

class CajaCsvExporterTest {

    private fun corte() = CorteCaja(
        id = "corte-1",
        sucursalId = "suc-1",
        usuarioId = "german",
        tipo = "parcial",
        fechaInicio = 1_700_000_000_000L, // 2023-11-14T22:13:20Z
        fechaFin = 1_700_003_600_000L,    // 2023-11-14T23:13:20Z
        totalVentas = BigDecimal("1500.00"),
        totalEfectivo = BigDecimal("900.00"),
        totalTarjeta = BigDecimal("600.00"),
        totalRetiros = BigDecimal("100.00"),
        montoEsperado = BigDecimal("800.00"),
        montoContado = BigDecimal("795.00"),
        diferencia = BigDecimal("-5.00"),
    )

    private fun retiro() = RetiroEfectivo(
        id = "retiro-1",
        sucursalId = "suc-1",
        usuarioId = "german",
        monto = BigDecimal("100.00"),
        motivo = "Pago a proveedor, contado",
        fecha = 1_700_001_200_000L, // 2023-11-14T22:33:20Z
    )

    @Test
    fun `dos bloques CORTES y RETIROS separados por una linea en blanco, con el formato exacto`() {
        val csv = CajaCsvExporter.generar(
            cortes = listOf(corte(), corte().copy(id = "corte-2", montoContado = null, diferencia = null)),
            retiros = listOf(retiro(), retiro().copy(id = "retiro-2", motivo = null)),
            zonaHoraria = ZoneOffset.UTC,
        )

        val esperado = buildString {
            append("CORTES\r\n")
            append(
                "id,tipo,fecha_inicio,fecha_fin,total_ventas,total_efectivo,total_tarjeta,total_retiros," +
                    "monto_esperado,monto_contado,diferencia\r\n",
            )
            append("corte-1,parcial,2023-11-14T22:13:20Z,2023-11-14T23:13:20Z,1500.00,900.00,600.00,100.00,800.00,795.00,-5.00\r\n")
            append("corte-2,parcial,2023-11-14T22:13:20Z,2023-11-14T23:13:20Z,1500.00,900.00,600.00,100.00,800.00,,\r\n")
            append("\r\n")
            append("RETIROS\r\n")
            append("id,fecha,monto,motivo\r\n")
            append("retiro-1,2023-11-14T22:33:20Z,100.00,\"Pago a proveedor, contado\"\r\n")
            append("retiro-2,2023-11-14T22:33:20Z,100.00,\r\n")
        }

        assertEquals(esperado, csv)
    }

    @Test
    fun `sin cortes ni retiros deja solo los encabezados de cada bloque`() {
        val csv = CajaCsvExporter.generar(cortes = emptyList(), retiros = emptyList(), zonaHoraria = ZoneOffset.UTC)

        val esperado = buildString {
            append("CORTES\r\n")
            append(
                "id,tipo,fecha_inicio,fecha_fin,total_ventas,total_efectivo,total_tarjeta,total_retiros," +
                    "monto_esperado,monto_contado,diferencia\r\n",
            )
            append("\r\n")
            append("RETIROS\r\n")
            append("id,fecha,monto,motivo\r\n")
        }

        assertEquals(esperado, csv)
    }

    @Test
    fun `las fechas se muestran en la zona horaria local, no en UTC crudo`() {
        // Hallazgo de verificacion en dispositivo (PLAN.md Parte 32): un
        // dispositivo en Mexico (UTC-6) leia "18:14" para una operacion
        // hecha a las 12:14 reales - el export mostraba Instant.toString()
        // (siempre UTC). fechaInicio/fechaFin/fecha corresponden al mismo
        // instante que en corte()/retiro(), solo cambia la zona con la que
        // se muestran.
        val mexico = ZoneOffset.ofHours(-6)

        val csv = CajaCsvExporter.generar(cortes = listOf(corte()), retiros = listOf(retiro()), zonaHoraria = mexico)

        assertEquals(
            "corte-1,parcial,2023-11-14T16:13:20-06:00,2023-11-14T17:13:20-06:00,1500.00,900.00,600.00,100.00,800.00,795.00,-5.00",
            csv.lines()[2],
        )
        assertEquals("retiro-1,2023-11-14T16:33:20-06:00,100.00,\"Pago a proveedor, contado\"", csv.lines()[6])
    }
}
