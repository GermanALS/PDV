package com.pdv.pos.domain.model

data class Sucursal(
    val id: String,
    val nombre: String,
    val direccion: String? = null,
    val activa: Boolean = true,
)
