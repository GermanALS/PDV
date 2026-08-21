package com.pdv.pos.usuario

import com.pdv.pos.domain.model.Usuario

// Catalogo de roles limitado a los dos de esta Parte (PLAN.md Parte 12);
// roles personalizados llegan en la Parte 13.
enum class RolUsuario {
    ADMINISTRADOR,
    ENCARGADO_TURNO,
}

fun RolUsuario.aTextoDominio(): String = when (this) {
    RolUsuario.ADMINISTRADOR -> "administrador"
    RolUsuario.ENCARGADO_TURNO -> "encargado_turno"
}

fun String.aRolUsuario(): RolUsuario = when (this) {
    "administrador" -> RolUsuario.ADMINISTRADOR
    else -> RolUsuario.ENCARGADO_TURNO
}

fun RolUsuario.etiqueta(): String = when (this) {
    RolUsuario.ADMINISTRADOR -> "Administrador"
    RolUsuario.ENCARGADO_TURNO -> "Encargado de turno"
}

data class UsuarioUiState(
    val usuarios: List<Usuario> = emptyList(),
    val usuarioEnEdicionId: String? = null,
    val username: String = "",
    val nombreCompleto: String = "",
    val rolSeleccionado: RolUsuario = RolUsuario.ENCARGADO_TURNO,
    val activo: Boolean = true,
    val error: String? = null,
    val mensajeConfirmacion: String? = null,
) {
    val editando: Boolean = usuarioEnEdicionId != null
}
