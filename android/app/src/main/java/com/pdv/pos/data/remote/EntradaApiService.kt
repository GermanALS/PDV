package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.EntradaCreateRequestDto
import com.pdv.pos.data.remote.dto.EntradaDto
import retrofit2.http.Body
import retrofit2.http.POST

interface EntradaApiService {
    @POST("entradas")
    suspend fun createEntrada(@Body request: EntradaCreateRequestDto): EntradaDto
}
