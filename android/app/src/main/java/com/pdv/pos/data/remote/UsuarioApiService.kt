package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.UsuarioCreateRequestDto
import com.pdv.pos.data.remote.dto.UsuarioDto
import com.pdv.pos.data.remote.dto.UsuarioListResponseDto
import com.pdv.pos.data.remote.dto.UsuarioUpdateRequestDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface UsuarioApiService {
    @GET("usuarios")
    suspend fun getUsuarios(): UsuarioListResponseDto

    @POST("usuarios")
    suspend fun createUsuario(@Body request: UsuarioCreateRequestDto): UsuarioDto

    @PATCH("usuarios/{id}")
    suspend fun updateUsuario(@Path("id") id: String, @Body request: UsuarioUpdateRequestDto): UsuarioDto

    @DELETE("usuarios/{id}")
    suspend fun deleteUsuario(@Path("id") id: String)
}
