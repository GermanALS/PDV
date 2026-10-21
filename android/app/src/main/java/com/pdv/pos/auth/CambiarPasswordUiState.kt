package com.pdv.pos.auth

data class CambiarPasswordUiState(
    val nuevaPassword: String = "",
    val confirmacion: String = "",
    val errorMessage: String? = null,
    val isGuardando: Boolean = false,
)
