package com.pdv.pos.ui

import com.pdv.pos.data.remote.ApiResult

data class HelloUiState(
    val localGreeting: String,
    val healthResult: ApiResult<String>? = null,
)
