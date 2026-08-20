package com.pdv.pos.entrada

import com.pdv.pos.domain.model.Articulo

enum class TipoEntrada {
    ARTICULO_NUEVO,
    ARTICULO_EXISTENTE,
}

enum class ObjetivoEscaneo {
    BUSQUEDA_EXISTENTE,
    CODIGO_BARRAS_NUEVO,
}

data class EntradaUiState(
    val tipo: TipoEntrada = TipoEntrada.ARTICULO_EXISTENTE,
    val busqueda: String = "",
    val articuloEncontrado: Articulo? = null,
    val errorBusqueda: String? = null,
    val escaneando: ObjetivoEscaneo? = null,
    val codigoBarras: String = "",
    val sku: String = "",
    val nombre: String = "",
    val descripcion: String = "",
    val categoria: String = "",
    val unidadMedida: String = "",
    val precioVenta: String = "",
    val costo: String = "",
    val cantidad: String = "",
    val ubicacion: String = "",
    // Opciones de EditableDropdownField (PLAN.md Parte 9, retroactivo a la
    // Parte 8 por decision explicita del usuario) derivadas de datos
    // estaticos de ejemplo mientras no exista InventarioRepository real.
    val categoriasDisponibles: List<String> = emptyList(),
    val unidadesMedidaDisponibles: List<String> = emptyList(),
    val ubicacionesDisponibles: List<String> = emptyList(),
    val mensajeConfirmacion: String? = null,
)
