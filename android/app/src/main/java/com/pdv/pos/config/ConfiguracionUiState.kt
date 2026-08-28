package com.pdv.pos.config

import com.pdv.pos.data.remote.ApiResult
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.Sucursal
import com.pdv.pos.ia.LlmProvider

data class ConfiguracionUiState(
    val ip: String = "",
    val puerto: String = "",
    val sucursales: List<Sucursal> = emptyList(),
    val sucursalSeleccionada: Sucursal? = null,
    val modo: BackendMode = BackendMode.LOCAL,
    val iaActivo: Boolean = false,
    val iaProveedor: LlmProvider = LlmProvider.DEEP_SEEK,
    val iaModelo: String = "",
    val iaTokenInput: String = "",
    val iaTieneTokenGuardado: Boolean = false,
    val iaProbandoConexion: Boolean = false,
    val iaResultadoPrueba: ApiResult<String>? = null,
    val promptIa: String = "",
    val promptIaPasswordInput: String = "",
    val promptIaError: String? = null,
    val promptIaGuardando: Boolean = false,
)
