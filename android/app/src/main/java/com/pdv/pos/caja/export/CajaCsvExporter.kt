package com.pdv.pos.caja.export

import com.pdv.pos.domain.model.CorteCaja
import com.pdv.pos.domain.model.RetiroEfectivo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Exportacion de cortes y retiros de un periodo (PLAN.md Parte 18,
// sub-parte I) a texto plano, sin librerias de terceros, RFC 4180 basico
// (comillas solo cuando el campo las necesita) - mismo criterio que
// InventarioCsvExporter. Un solo archivo con dos bloques encabezados por
// una linea "CORTES" y una linea "RETIROS", separados por una linea en
// blanco. Montos con toPlainString y nulos vacios.
//
// Fechas en ISO-8601 con el offset de la zona horaria LOCAL del
// dispositivo (PLAN.md Parte 32, hallazgo de verificacion en dispositivo:
// el export original mostraba UTC crudo - "Instant.toString()" siempre
// termina en "Z" - lo que en Mexico (UTC-6) leia "18:14" para una
// operacion hecha a las 12:14 reales, confuso para un negocio de una sola
// sucursal en una sola zona horaria). El offset explicito (vs. solo hora
// local sin offset) mantiene el archivo inequivoco si se reprocesa en otra
// zona.
object CajaCsvExporter {

    private val ENCABEZADOS_CORTES = listOf(
        "id", "tipo", "fecha_inicio", "fecha_fin", "total_ventas", "total_efectivo",
        "total_tarjeta", "total_retiros", "monto_esperado", "monto_contado", "diferencia",
    )

    private val ENCABEZADOS_RETIROS = listOf("id", "fecha", "monto", "motivo")

    fun generar(
        cortes: List<CorteCaja>,
        retiros: List<RetiroEfectivo>,
        zonaHoraria: ZoneId = ZoneId.systemDefault(),
    ): String {
        val lineas = mutableListOf<String>()
        lineas += "CORTES"
        lineas += ENCABEZADOS_CORTES.joinToString(",")
        cortes.forEach { corte -> lineas += corte.toFila(zonaHoraria).joinToString(",") { it.escapar() } }
        lineas += ""
        lineas += "RETIROS"
        lineas += ENCABEZADOS_RETIROS.joinToString(",")
        retiros.forEach { retiro -> lineas += retiro.toFila(zonaHoraria).joinToString(",") { it.escapar() } }
        return lineas.joinToString("\r\n") + "\r\n"
    }

    private fun CorteCaja.toFila(zona: ZoneId): List<String> = listOf(
        id,
        tipo,
        fechaLocal(fechaInicio, zona),
        fechaLocal(fechaFin, zona),
        totalVentas.toPlainString(),
        totalEfectivo.toPlainString(),
        totalTarjeta.toPlainString(),
        totalRetiros.toPlainString(),
        montoEsperado.toPlainString(),
        montoContado?.toPlainString().orEmpty(),
        diferencia?.toPlainString().orEmpty(),
    )

    private fun RetiroEfectivo.toFila(zona: ZoneId): List<String> = listOf(
        id,
        fechaLocal(fecha, zona),
        monto.toPlainString(),
        motivo.orEmpty(),
    )

    private fun fechaLocal(epochMillis: Long, zona: ZoneId): String =
        Instant.ofEpochMilli(epochMillis).atZone(zona).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    private fun String.escapar(): String =
        if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${replace("\"", "\"\"")}\""
        } else {
            this
        }
}
