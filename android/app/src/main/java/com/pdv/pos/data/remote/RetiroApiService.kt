package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.RetiroEfectivoCreateRequestDto
import com.pdv.pos.data.remote.dto.RetiroEfectivoDto
import com.pdv.pos.data.remote.dto.RetiroEfectivoListResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface RetiroApiService {
    @POST("retiros-efectivo")
    suspend fun createRetiro(@Body request: RetiroEfectivoCreateRequestDto): RetiroEfectivoDto

    @GET("retiros-efectivo")
    suspend fun getRetiros(
        @Query("sucursal_id") sucursalId: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): RetiroEfectivoListResponseDto
}
