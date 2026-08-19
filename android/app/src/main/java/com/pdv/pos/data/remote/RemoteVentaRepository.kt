package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.VentaCreateRequestDto
import com.pdv.pos.data.remote.dto.VentaDetalleCreateRequestDto
import com.pdv.pos.domain.model.Venta
import com.pdv.pos.domain.model.VentaLinea
import com.pdv.pos.domain.repository.VentaRepository
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteVentaRepository @Inject constructor(
    private val api: VentaApiService,
    private val appLogger: AppLogger,
) : VentaRepository {

    override suspend fun registrarVenta(venta: Venta) {
        try {
            api.createVenta(venta.toCreateRequestDto())
        } catch (e: IOException) {
            logFallo(venta, e)
            throw e
        } catch (e: HttpException) {
            logFallo(venta, e)
            throw e
        }
    }

    private suspend fun logFallo(venta: Venta, e: Exception) {
        appLogger.log(
            LogType.ERROR,
            sucursalId = venta.sucursalId,
            usuario = venta.usuarioId,
            mensaje = "Fallo de red al registrar venta: folio=${venta.folio}, ${e.message}",
        )
    }
}

private fun Venta.toCreateRequestDto() = VentaCreateRequestDto(
    localId = id,
    sucursalId = sucursalId,
    usuarioId = usuarioId,
    folio = folio,
    fecha = Instant.ofEpochMilli(fecha).toString(),
    subtotal = subtotal.toPlainString(),
    descuento = descuento.toPlainString(),
    impuestos = impuestos.toPlainString(),
    total = total.toPlainString(),
    metodoPago = metodoPago,
    estado = estado,
    lineas = lineas.map { it.toCreateRequestDto() },
)

private fun VentaLinea.toCreateRequestDto() = VentaDetalleCreateRequestDto(
    articuloId = articuloId,
    cantidad = cantidad.toPlainString(),
    precioUnitario = precioUnitario.toPlainString(),
    subtotal = subtotal.toPlainString(),
)
