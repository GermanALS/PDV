package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.RetiroEfectivoCreateRequestDto
import com.pdv.pos.data.remote.dto.RetiroEfectivoDto
import com.pdv.pos.domain.model.RetiroEfectivo
import com.pdv.pos.domain.repository.RetiroEfectivoRepository
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import java.io.IOException
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

// Mismo criterio que RemoteCajaRepository: CajaScreen no pagina el
// historial de retiros, alcanza con la tanda mas reciente.
private const val TAMANIO_PAGINA_HISTORIAL = 50

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

    // Un solo disparo, no una suscripcion real - ver comentario de
    // RemoteCajaRepository.observeCortes.
    override fun observeRetiros(sucursalId: String): Flow<List<RetiroEfectivo>> = flow {
        val respuesta = api.getRetiros(sucursalId = sucursalId, page = 1, pageSize = TAMANIO_PAGINA_HISTORIAL)
        emit(respuesta.items.map { it.toDomain() })
    }
}

private fun RetiroEfectivoDto.toDomain() = RetiroEfectivo(
    id = localId ?: id,
    sucursalId = sucursalId,
    usuarioId = usuarioId,
    monto = BigDecimal(monto),
    motivo = motivo,
    fecha = Instant.parse(fecha).toEpochMilli(),
)

private fun RetiroEfectivo.toCreateRequestDto() = RetiroEfectivoCreateRequestDto(
    localId = id,
    sucursalId = sucursalId,
    usuarioId = usuarioId,
    monto = monto.toPlainString(),
    motivo = motivo,
    fecha = Instant.ofEpochMilli(fecha).toString(),
)
