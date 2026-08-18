package com.pdv.pos.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponseDto(
    val status: String,
    val version: String,
)
