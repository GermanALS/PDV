package com.pdv.pos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthLoginRequestDto(
    val username: String,
    val password: String,
)

@Serializable
data class AuthLoginResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    val usuario: UsuarioDto,
)
