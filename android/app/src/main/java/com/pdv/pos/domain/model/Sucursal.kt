package com.pdv.pos.domain.model

data class Sucursal(
    val id: String,
    val nombre: String,
    val direccion: String? = null,
    val activa: Boolean = true,
    // Id de la misma sucursal en el backend: relaciona la copia local con la
    // remota, cuyos ids crudos difieren (PLAN.md Parte 36, A-8).
    val remoteId: String? = null,
)
