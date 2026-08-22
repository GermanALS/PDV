package com.pdv.pos.domain.model

data class Usuario(
    val id: String,
    val username: String,
    val nombreCompleto: String,
    val rolId: String,
    val activo: Boolean = true,
)
