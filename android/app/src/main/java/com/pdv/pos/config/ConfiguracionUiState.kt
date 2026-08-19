package com.pdv.pos.config

import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.Sucursal

data class PermisoModulo(val nombreModulo: String, val habilitado: Boolean)

data class ConfiguracionUiState(
    val ip: String = "",
    val puerto: String = "",
    val nombreBaseDatos: String = "",
    val sucursales: List<Sucursal> = emptyList(),
    val sucursalSeleccionada: Sucursal? = null,
    val modo: BackendMode = BackendMode.LOCAL,
    val permisosSimulados: List<PermisoModulo> = emptyList(),
)
