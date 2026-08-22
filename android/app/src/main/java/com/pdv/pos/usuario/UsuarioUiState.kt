package com.pdv.pos.usuario

import com.pdv.pos.domain.model.Rol
import com.pdv.pos.domain.model.Usuario

data class UsuarioUiState(
    val usuarios: List<Usuario> = emptyList(),
    val roles: List<Rol> = emptyList(),
    val usuarioEnEdicionId: String? = null,
    val username: String = "",
    val nombreCompleto: String = "",
    val rolIdSeleccionado: String? = null,
    val activo: Boolean = true,
    // Texto plano en memoria mientras se edita el formulario; se hashea al
    // guardar y nunca se persiste ni se envia asi (PLAN.md Parte 13,
    // Decision 2). Vacio = no cambiar la contrasena existente al editar.
    val password: String = "",
    val error: String? = null,
    val mensajeConfirmacion: String? = null,
) {
    val editando: Boolean = usuarioEnEdicionId != null
}

fun List<Rol>.nombreDe(rolId: String): String = find { it.id == rolId }?.nombre ?: rolId
