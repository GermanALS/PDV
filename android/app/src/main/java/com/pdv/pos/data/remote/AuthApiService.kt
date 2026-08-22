package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.AuthLoginRequestDto
import com.pdv.pos.data.remote.dto.AuthLoginResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {
    @POST("auth/login")
    suspend fun login(@Body request: AuthLoginRequestDto): AuthLoginResponseDto
}
