package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.HealthResponseDto
import retrofit2.http.GET

interface HealthApiService {
    @GET("health")
    suspend fun getHealth(): HealthResponseDto
}
