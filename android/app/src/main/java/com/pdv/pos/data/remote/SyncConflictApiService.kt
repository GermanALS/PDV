package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.SyncConflictCreateRequestDto
import com.pdv.pos.data.remote.dto.SyncConflictDto
import com.pdv.pos.data.remote.dto.SyncConflictListResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface SyncConflictApiService {
    @GET("sync-conflicts")
    suspend fun getSyncConflicts(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 100,
    ): SyncConflictListResponseDto

    // El backend responde 201 (nuevo) o 200 (ya existia, reintento de sync);
    // Retrofit acepta cualquier 2xx, la distincion no interesa al cliente.
    @POST("sync-conflicts")
    suspend fun createSyncConflict(@Body request: SyncConflictCreateRequestDto): SyncConflictDto
}
