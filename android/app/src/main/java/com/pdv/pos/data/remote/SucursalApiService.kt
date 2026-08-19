package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.SucursalCreateRequestDto
import com.pdv.pos.data.remote.dto.SucursalDto
import com.pdv.pos.data.remote.dto.SucursalListResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface SucursalApiService {
    @GET("sucursales")
    suspend fun getSucursales(): SucursalListResponseDto

    @POST("sucursales")
    suspend fun createSucursal(@Body request: SucursalCreateRequestDto): SucursalDto
}
