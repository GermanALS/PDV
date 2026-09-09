package com.pdv.pos.auth

import com.pdv.pos.data.remote.ApiResult
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.EsquemaConexion

data class PanelConexionUiState(
    val modo: BackendMode = BackendMode.LOCAL,
    val esquema: EsquemaConexion = EsquemaConexion.HTTP,
    val host: String = "",
    val puerto: String = "",
    val probando: Boolean = false,
    val resultadoPrueba: ApiResult<String>? = null,
)
