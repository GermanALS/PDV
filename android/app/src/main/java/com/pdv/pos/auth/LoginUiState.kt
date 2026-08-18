package com.pdv.pos.auth

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val errorMessage: String? = null,
)
