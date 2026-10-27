package com.pdv.pos.ui

import com.pdv.pos.domain.model.BackendMode

data class HelloUiState(
    val username: String,
    val rolNombre: String? = null,
    val sucursalNombre: String? = null,
    // null hasta leer la configuracion: sin modo conocido no se muestra el
    // estado de conexion.
    val backendMode: BackendMode? = null,
    val conexion: EstadoConexion? = null,
    val modulosPermitidos: Set<String> = emptySet(),
)

/** Server reachability shown on the home screen (PLAN.md Parte 40, grupo 7). */
enum class EstadoConexion { SOLO_LOCAL, COMPROBANDO, CONECTADO, SIN_CONEXION }
