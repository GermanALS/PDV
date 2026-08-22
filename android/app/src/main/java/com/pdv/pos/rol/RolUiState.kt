package com.pdv.pos.rol

import com.pdv.pos.domain.model.Rol

// Mismas 8 claves de modulo que docs/api-contract.md Seccion 10 y
// docs/schema-pos.json (PLAN.md Parte 13; "ia" agregada en la Parte 14).
val MODULOS_DISPONIBLES = listOf("venta", "entrada", "inventario", "caja", "devoluciones", "usuarios", "configuracion", "ia")

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
