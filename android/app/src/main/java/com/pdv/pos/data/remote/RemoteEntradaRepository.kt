package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.ArticuloNuevoRequestDto
import com.pdv.pos.data.remote.dto.EntradaCreateRequestDto
import com.pdv.pos.domain.model.ArticuloNuevo
import com.pdv.pos.domain.model.Entrada
import com.pdv.pos.domain.repository.EntradaRepository
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteEntradaRepository @Inject constructor(
    private val api: EntradaApiService,
    private val appLogger: AppLogger,
) : EntradaRepository {

    override suspend fun registrarEntrada(entrada: Entrada) {
        try {
            api.createEntrada(entrada.toCreateRequestDto())
        } catch (e: IOException) {
            logFallo(entrada, e)
            throw e
        } catch (e: HttpException) {
            logFallo(entrada, e)
            throw e
        }
    }

    private suspend fun logFallo(entrada: Entrada, e: Exception) {
        appLogger.log(
            LogType.ERROR,
            sucursalId = entrada.sucursalId,
            usuario = entrada.usuarioId,
            mensaje = "Fallo de red al registrar entrada: ${e.message}",
        )
    }
}

private fun Entrada.toCreateRequestDto() = EntradaCreateRequestDto(
    localId = id,
    sucursalId = sucursalId,
    usuarioId = usuarioId,
    fecha = Instant.ofEpochMilli(fecha).toString(),
    cantidad = cantidad.toPlainString(),
    ubicacion = ubicacion,
    articuloId = (this as? Entrada.DeArticuloExistente)?.articuloId,
    articuloNuevo = (this as? Entrada.DeArticuloNuevo)?.articulo?.toRequestDto(),
)

private fun ArticuloNuevo.toRequestDto() = ArticuloNuevoRequestDto(
    localId = id,
    codigoBarras = codigoBarras,
    sku = sku,
    nombre = nombre,
    descripcion = descripcion,
    categoria = categoria,
    unidadMedida = unidadMedida,
    precioVenta = precioVenta.toPlainString(),
    costo = costo?.toPlainString(),
)
