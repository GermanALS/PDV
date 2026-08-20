package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.RetiroEfectivoCreateRequestDto
import com.pdv.pos.domain.model.RetiroEfectivo
import com.pdv.pos.domain.repository.RetiroEfectivoRepository
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteRetiroEfectivoRepository @Inject constructor(
    private val api: RetiroApiService,
    private val appLogger: AppLogger,
) : RetiroEfectivoRepository {

    override suspend fun registrarRetiro(retiro: RetiroEfectivo) {
        try {
            api.createRetiro(retiro.toCreateRequestDto())
        } catch (e: IOException) {
            logFallo(retiro, e)
            throw e
        } catch (e: HttpException) {
            logFallo(retiro, e)
            throw e
        }
    }

    private suspend fun logFallo(retiro: RetiroEfectivo, e: Exception) {
        appLogger.log(
            LogType.ERROR,
            sucursalId = retiro.sucursalId,
            usuario = retiro.usuarioId,
            mensaje = "Fallo de red al registrar retiro de efectivo: monto=${retiro.monto}, ${e.message}",
        )
    }
}

private fun RetiroEfectivo.toCreateRequestDto() = RetiroEfectivoCreateRequestDto(
    localId = id,
    sucursalId = sucursalId,
    usuarioId = usuarioId,
    monto = monto.toPlainString(),
    motivo = motivo,
    fecha = Instant.ofEpochMilli(fecha).toString(),
)
