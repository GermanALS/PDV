package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.RetiroEfectivoCreateRequestDto
import com.pdv.pos.data.remote.dto.RetiroEfectivoDto
import retrofit2.http.Body
import retrofit2.http.POST

interface RetiroApiService {
    @POST("retiros-efectivo")
    suspend fun createRetiro(@Body request: RetiroEfectivoCreateRequestDto): RetiroEfectivoDto
}
