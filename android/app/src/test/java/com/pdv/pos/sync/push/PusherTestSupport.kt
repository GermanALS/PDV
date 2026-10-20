package com.pdv.pos.sync.push

import com.pdv.pos.data.local.SyncConflictDao
import io.mockk.mockk
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

internal fun httpException(code: Int): HttpException =
    HttpException(Response.error<Any>(code, "{}".toResponseBody("application/json".toMediaType())))

internal fun registroRechazos(conflictDao: SyncConflictDao = mockk(relaxUnitFun = true)) =
    RegistroRechazos(conflictDao, mockk(relaxed = true))
