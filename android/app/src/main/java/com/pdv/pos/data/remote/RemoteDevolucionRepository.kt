package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.DevolucionCreateRequestDto
import com.pdv.pos.data.remote.dto.DevolucionDetalleCreateRequestDto
import com.pdv.pos.domain.model.Devolucion
import com.pdv.pos.domain.model.DevolucionLinea
import com.pdv.pos.domain.repository.DevolucionRepository
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteDevolucionRepository @Inject constructor(
    private val api: DevolucionApiService,
    private val appLogger: AppLogger,
) : DevolucionRepository {

    override suspend fun registrarDevolucion(devolucion: Devolucion) {
        try {
            api.createDevolucion(devolucion.toCreateRequestDto())
        } catch (e: IOException) {
            logFallo(devolucion, e)
            throw e
        } catch (e: HttpException) {
            logFallo(devolucion, e)
            throw e
        }
    }

    private suspend fun logFallo(devolucion: Devolucion, e: Exception) {
        appLogger.log(
            LogType.ERROR,
            sucursalId = devolucion.sucursalId,
            usuario = devolucion.usuarioId,
            mensaje = "Fallo de red al registrar devolución: folio=${devolucion.folio}, ${e.message}",
        )
    }
}

private fun Devolucion.toCreateRequestDto() = DevolucionCreateRequestDto(
    localId = id,
    sucursalId = sucursalId,
    usuarioId = usuarioId,
    ventaId = ventaId,
    folio = folio,
    fecha = Instant.ofEpochMilli(fecha).toString(),
    estado = estado,
    lineas = lineas.map { it.toCreateRequestDto() },
)

private fun DevolucionLinea.toCreateRequestDto() = DevolucionDetalleCreateRequestDto(
    articuloId = articuloId,
    cantidad = cantidad.toPlainString(),
    motivo = motivo,
    condicion = condicion,
)
