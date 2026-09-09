package com.pdv.pos.auth

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val errorMessage: String? = null,
    // El ultimo fallo fue por conectividad (backend inalcanzable o
    // respondiendo con error), no por credenciales. Habilita la accion
    // directa "Configurar conexion" en la pantalla de login (PLAN.md Parte
    // 31, Grupo 2).
    val connectivityError: Boolean = false,
)
