package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.DevolucionCreateRequestDto
import com.pdv.pos.data.remote.dto.DevolucionDto
import retrofit2.http.Body
import retrofit2.http.POST

interface DevolucionApiService {
    @POST("devoluciones")
    suspend fun createDevolucion(@Body request: DevolucionCreateRequestDto): DevolucionDto
}
