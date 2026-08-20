package com.pdv.pos.inventario

import com.pdv.pos.domain.model.InventarioItem
import com.pdv.pos.domain.model.PaginaInventario
import com.pdv.pos.inventario.export.ArchivoExportado
import java.math.BigDecimal

val TAMANIOS_PAGINA_DISPONIBLES = listOf(10, 20, 50)

data class InventarioUiState(
    val resultado: PaginaInventario = PaginaInventario(
        items = emptyList(),
        pagina = 1,
        tamanioPagina = TAMANIOS_PAGINA_DISPONIBLES.first(),
        total = 0,
    ),
    val busqueda: String = "",
    val escaneando: Boolean = false,
    val categoriasDisponibles: List<String> = emptyList(),
    val unidadesMedidaDisponibles: List<String> = emptyList(),
    val ubicacionesDisponibles: List<String> = emptyList(),
    val edicion: EdicionArticuloUiState? = null,
    val exportando: Boolean = false,
    val archivoParaCompartir: ArchivoExportado? = null,
    val mensajeConfirmacion: String? = null,
) {
    val itemsPagina: List<InventarioItem> get() = resultado.items
    val paginaMostrada: Int get() = resultado.pagina
    val tamanioPagina: Int get() = resultado.tamanioPagina
    val totalPaginas: Int get() = resultado.totalPaginas
}

// Cantidad actual se conserva aparte del campo editable "cantidad" porque el
// guardado la trata como ajuste (delta = nueva - conocida), nunca como
// sobrescritura directa (PLAN.md Parte 6, "Decisiones abiertas").
data class EdicionArticuloUiState(
    val articuloId: String,
    val nombre: String,
    val descripcion: String,
    val categoria: String,
    val unidadMedida: String,
    val precioVenta: String,
    val costo: String,
    val cantidadActual: BigDecimal,
    val cantidad: String,
    val ubicacion: String,
)
