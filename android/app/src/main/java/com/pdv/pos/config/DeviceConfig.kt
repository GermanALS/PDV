package com.pdv.pos.config

import com.pdv.pos.domain.model.BackendMode

data class DeviceConfig(
    val backendMode: BackendMode = BackendMode.LOCAL,
    val ip: String = "",
    val puerto: String = "",
    val sucursalIdSeleccionada: String? = null,
)
