package com.pdv.pos.rol

import com.pdv.pos.domain.model.Rol

// Mismas 7 claves de modulo que docs/api-contract.md Seccion 10 y
// docs/schema-pos.json (PLAN.md Parte 13).
val MODULOS_DISPONIBLES = listOf("venta", "entrada", "inventario", "caja", "devoluciones", "usuarios", "configuracion")

data class RolUiState(
    val roles: List<Rol> = emptyList(),
    val rolEnEdicionId: String? = null,
    val esSistemaEnEdicion: Boolean = false,
    val nombre: String = "",
    val modulosSeleccionados: Set<String> = emptySet(),
    val error: String? = null,
    val mensajeConfirmacion: String? = null,
) {
    val editando: Boolean = rolEnEdicionId != null
}
