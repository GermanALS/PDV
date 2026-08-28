package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.CorteCajaCreateRequestDto
import com.pdv.pos.data.remote.dto.CorteCajaDto
import com.pdv.pos.data.remote.dto.CorteCajaListResponseDto
import com.pdv.pos.data.remote.dto.TotalesCorteDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface CajaApiService {
    @POST("cortes-caja")
    suspend fun createCorte(@Body request: CorteCajaCreateRequestDto): CorteCajaDto

    @GET("cortes-caja/totales")
    suspend fun getTotales(
        @Query("sucursal_id") sucursalId: String,
        @Query("fecha_inicio") fechaInicio: String,
        @Query("fecha_fin") fechaFin: String,
    ): TotalesCorteDto

    @GET("cortes-caja")
    suspend fun getCortes(
        @Query("sucursal_id") sucursalId: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): CorteCajaListResponseDto
}
