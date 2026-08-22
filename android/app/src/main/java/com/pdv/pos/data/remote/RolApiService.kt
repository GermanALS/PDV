package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.RolCreateRequestDto
import com.pdv.pos.data.remote.dto.RolDto
import com.pdv.pos.data.remote.dto.RolListResponseDto
import com.pdv.pos.data.remote.dto.RolUpdateRequestDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface RolApiService {
    @GET("roles")
    suspend fun getRoles(): RolListResponseDto

    @POST("roles")
    suspend fun createRol(@Body request: RolCreateRequestDto): RolDto

    @PATCH("roles/{id}")
    suspend fun updateRol(@Path("id") id: String, @Body request: RolUpdateRequestDto): RolDto

    @DELETE("roles/{id}")
    suspend fun deleteRol(@Path("id") id: String)
}
