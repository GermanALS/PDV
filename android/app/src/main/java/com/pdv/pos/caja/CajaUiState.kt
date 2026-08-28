package com.pdv.pos.caja

import com.pdv.pos.domain.model.CorteCaja
import com.pdv.pos.domain.model.RetiroEfectivo
import com.pdv.pos.inventario.export.ArchivoExportado
import java.math.BigDecimal

enum class TipoCorte {
    PARCIAL,
    FINAL,
}

fun TipoCorte.aTextoDominio(): String = when (this) {
    TipoCorte.PARCIAL -> "parcial"
    TipoCorte.FINAL -> "final"
}

data class CajaUiState(
    val tipoCorte: TipoCorte,
    val fechaInicio: Long,
    val fechaFin: Long,
    val calculado: Boolean = false,
    val totalVentas: BigDecimal = BigDecimal.ZERO,
    val totalEfectivo: BigDecimal = BigDecimal.ZERO,
    val totalTarjeta: BigDecimal = BigDecimal.ZERO,
    val totalRetiros: BigDecimal = BigDecimal.ZERO,
    val montoContado: String = "",
    val historialCortes: List<CorteCaja> = emptyList(),
    val mensajeConfirmacion: String? = null,
    val mostrarDialogoRetiro: Boolean = false,
    val montoRetiro: String = "",
    val motivoRetiro: String = "",
    val errorRetiro: String? = null,
    val historialRetiros: List<RetiroEfectivo> = emptyList(),
    // Exportacion de cortes y retiros por periodo (PLAN.md Parte 18,
    // sub-parte I). El dialogo arranca con los ultimos 7 dias, editable a
    // cualquier rango. archivoExportado se consume una sola vez desde
    // CajaScreen para abrir el share sheet.
    val mostrarDialogoExportar: Boolean = false,
    val exportarDesde: Long = 0L,
    val exportarHasta: Long = 0L,
    val exportando: Boolean = false,
    val archivoExportado: ArchivoExportado? = null,
) {
    // El periodo del corte parcial es de solo lectura (calculado); solo el
    // final admite modificar el horario (PLAN.md Parte 10).
    val periodoEditable: Boolean = tipoCorte == TipoCorte.FINAL

    // monto_esperado es el efectivo que deberia haber en caja: ventas en
    // efectivo del periodo menos lo que ya se retiro fisicamente.
    val montoEsperado: BigDecimal = totalEfectivo - totalRetiros

    val diferencia: BigDecimal? = montoContado.toBigDecimalOrNull()?.let { it - montoEsperado }
}
