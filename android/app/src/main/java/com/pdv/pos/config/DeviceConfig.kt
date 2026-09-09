package com.pdv.pos.config

import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.EsquemaConexion

data class DeviceConfig(
    val backendMode: BackendMode = BackendMode.LOCAL,
    val esquema: EsquemaConexion = EsquemaConexion.HTTP,
    val ip: String = "",
    val puerto: String = "",
    val sucursalIdSeleccionada: String? = null,
)
