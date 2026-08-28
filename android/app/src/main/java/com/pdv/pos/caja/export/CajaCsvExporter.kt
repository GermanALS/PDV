package com.pdv.pos.caja.export

import com.pdv.pos.domain.model.CorteCaja
import com.pdv.pos.domain.model.RetiroEfectivo
import java.time.Instant

// Exportacion de cortes y retiros de un periodo (PLAN.md Parte 18,
// sub-parte I) a texto plano, sin librerias de terceros, RFC 4180 basico
// (comillas solo cuando el campo las necesita) - mismo criterio que
// InventarioCsvExporter. Un solo archivo con dos bloques encabezados por
// una linea "CORTES" y una linea "RETIROS", separados por una linea en
// blanco. Fechas en ISO-8601 UTC; montos con toPlainString y nulos vacios.
object CajaCsvExporter {

    private val ENCABEZADOS_CORTES = listOf(
        "id", "tipo", "fecha_inicio", "fecha_fin", "total_ventas", "total_efectivo",
        "total_tarjeta", "total_retiros", "monto_esperado", "monto_contado", "diferencia",
    )

    private val ENCABEZADOS_RETIROS = listOf("id", "fecha", "monto", "motivo")

    fun generar(cortes: List<CorteCaja>, retiros: List<RetiroEfectivo>): String {
        val lineas = mutableListOf<String>()
        lineas += "CORTES"
        lineas += ENCABEZADOS_CORTES.joinToString(",")
        cortes.forEach { corte -> lineas += corte.toFila().joinToString(",") { it.escapar() } }
        lineas += ""
        lineas += "RETIROS"
        lineas += ENCABEZADOS_RETIROS.joinToString(",")
        retiros.forEach { retiro -> lineas += retiro.toFila().joinToString(",") { it.escapar() } }
        return lineas.joinToString("\r\n") + "\r\n"
    }

    private fun CorteCaja.toFila(): List<String> = listOf(
        id,
        tipo,
        fechaIso(fechaInicio),
        fechaIso(fechaFin),
        totalVentas.toPlainString(),
        totalEfectivo.toPlainString(),
        totalTarjeta.toPlainString(),
        totalRetiros.toPlainString(),
        montoEsperado.toPlainString(),
        montoContado?.toPlainString().orEmpty(),
        diferencia?.toPlainString().orEmpty(),
    )

    private fun RetiroEfectivo.toFila(): List<String> = listOf(
        id,
        fechaIso(fecha),
        monto.toPlainString(),
        motivo.orEmpty(),
    )

    private fun fechaIso(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()

    private fun String.escapar(): String =
        if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${replace("\"", "\"\"")}\""
        } else {
            this
        }
}
