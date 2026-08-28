package com.pdv.pos.inventario.importacion

data class ImportacionCatalogoUiState(
    val procesando: Boolean = false,
    val procesados: Int = 0,
    val total: Int = 0,
    val encabezadoInvalido: Boolean = false,
    val mensaje: String? = null,
    val resumen: ResumenImportacion? = null,
)
