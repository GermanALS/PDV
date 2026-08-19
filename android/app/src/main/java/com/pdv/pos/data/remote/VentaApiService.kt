package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.VentaCreateRequestDto
import com.pdv.pos.data.remote.dto.VentaDto
import retrofit2.http.Body
import retrofit2.http.POST

interface VentaApiService {
    @POST("ventas")
    suspend fun createVenta(@Body request: VentaCreateRequestDto): VentaDto
}
