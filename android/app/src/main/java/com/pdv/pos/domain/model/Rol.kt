package com.pdv.pos.domain.model

data class Rol(
    val id: String,
    val nombre: String,
    val modulosPermitidos: List<String>,
    val esSistema: Boolean = false,
)

// PLAN.md Parte 13, checklist "Roles y permisos": modulosPermitidos activa
// o desactiva pantallas completas, no acciones dentro de un modulo.
fun Usuario.modulosPermitidos(roles: List<Rol>): Set<String> =
    roles.find { it.id == rolId }?.modulosPermitidos?.toSet() ?: emptySet()
