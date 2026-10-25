package com.pdv.pos.ui

import com.pdv.pos.data.remote.ApiResult
import com.pdv.pos.domain.model.BackendMode

data class HelloUiState(
    val username: String,
    // null hasta leer la configuracion: sin modo conocido no se muestra el
    // estado del backend.
    val backendMode: BackendMode? = null,
    val healthResult: ApiResult<String>? = null,
    val modulosPermitidos: Set<String> = emptySet(),
)
