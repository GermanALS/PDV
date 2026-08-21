package com.pdv.pos.devolucion

import com.pdv.pos.domain.model.Articulo
import java.math.BigDecimal

enum class Condicion {
    DEFECTUOSO,
    NO_DEFECTUOSO,
}

fun Condicion.aTextoDominio(): String = when (this) {
    Condicion.DEFECTUOSO -> "defectuoso"
    Condicion.NO_DEFECTUOSO -> "no_defectuoso"
}

// id propio (no articulo.id): el mismo articulo puede agregarse mas de una
// vez con distinta condicion/motivo/cantidad (ej. 2 unidades defectuosas +
// 1 no defectuosa del mismo producto), a diferencia del carrito de Venta
// que garantiza una sola linea por articulo (hallazgo de code-reviewer).
data class LineaDevolucion(
    val id: String,
    val articulo: Articulo,
    val cantidad: BigDecimal,
    val motivo: String?,
    val condicion: Condicion,
)

data class DevolucionRegistrada(
    val folio: String,
    val cantidadLineas: Int,
    val estado: String,
)

data class DevolucionUiState(
    val busqueda: String = "",
    val articuloEncontrado: Articulo? = null,
    val errorBusqueda: String? = null,
    val cantidadIngresada: String = "1",
    val motivoIngresado: String = "",
    val condicionSeleccionada: Condicion = Condicion.DEFECTUOSO,
    val errorLinea: String? = null,
    val lineas: List<LineaDevolucion> = emptyList(),
    val mostrarEscaner: Boolean = false,
    val ventaOriginal: String = "",
    val mensajeConfirmacion: String? = null,
    val historial: List<DevolucionRegistrada> = emptyList(),
)
