package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.CorteCajaCreateRequestDto
import com.pdv.pos.data.remote.dto.CorteCajaDto
import com.pdv.pos.domain.model.CorteCaja
import com.pdv.pos.domain.model.TotalesCorte
import com.pdv.pos.domain.repository.CajaRepository
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

// Tamanio de pagina fijo para el historial reactivo (PLAN.md Parte 18,
// sub-parte F) - CajaScreen no pagina su historial (a diferencia de
// Inventario), asi que alcanza con traer la tanda mas reciente.
private const val TAMANIO_PAGINA_HISTORIAL = 50

@Singleton
class RemoteCajaRepository @Inject constructor(
    private val api: CajaApiService,
    private val appLogger: AppLogger,
) : CajaRepository {

    // Lectura: propaga fallas de red sin capturarlas, mismo criterio que
    // RemoteInventarioRepository.observarInventario (no trae usuarioId
    // para el log; AppLogger.log lo exige).
    override suspend fun calcularTotales(sucursalId: String, fechaInicio: Long, fechaFin: Long): TotalesCorte {
        val dto = api.getTotales(
            sucursalId = sucursalId,
            fechaInicio = Instant.ofEpochMilli(fechaInicio).toString(),
            fechaFin = Instant.ofEpochMilli(fechaFin).toString(),
        )
        return TotalesCorte(
            totalVentas = BigDecimal(dto.totalVentas),
            totalEfectivo = BigDecimal(dto.totalEfectivo),
            totalTarjeta = BigDecimal(dto.totalTarjeta),
            totalRetiros = BigDecimal(dto.totalRetiros),
            montoEsperado = BigDecimal(dto.montoEsperado),
        )
    }

    override suspend fun guardarCorte(corte: CorteCaja) {
        try {
            api.createCorte(corte.toCreateRequestDto())
        } catch (e: IOException) {
            logFallo(corte, e)
            throw e
        } catch (e: HttpException) {
            logFallo(corte, e)
            throw e
        }
    }

    private suspend fun logFallo(corte: CorteCaja, e: Exception) {
        appLogger.log(
            LogType.ERROR,
            sucursalId = corte.sucursalId,
            usuario = corte.usuarioId,
            mensaje = "Fallo de red al guardar corte de caja: tipo=${corte.tipo}, ${e.message}",
        )
    }

    // Un solo disparo, no una suscripcion real (mismo criterio que
    // RemoteInventarioRepository.observarInventario) - CajaRefreshSignal
    // (ModeAwareCajaRepository) es lo que fuerza un nuevo llamado tras una
    // escritura externa.
    override fun observeCortes(sucursalId: String): Flow<List<CorteCaja>> = flow {
        val respuesta = api.getCortes(sucursalId = sucursalId, page = 1, pageSize = TAMANIO_PAGINA_HISTORIAL)
        emit(respuesta.items.map { it.toDomain() })
    }
}

private fun CorteCajaDto.toDomain() = CorteCaja(
    id = localId ?: id,
    sucursalId = sucursalId,
    usuarioId = usuarioId,
    tipo = tipo,
    fechaInicio = Instant.parse(fechaInicio).toEpochMilli(),
    fechaFin = Instant.parse(fechaFin).toEpochMilli(),
    totalVentas = BigDecimal(totalVentas),
    totalEfectivo = BigDecimal(totalEfectivo),
    totalTarjeta = BigDecimal(totalTarjeta),
    totalRetiros = BigDecimal(totalRetiros),
    montoEsperado = BigDecimal(montoEsperado),
    montoContado = montoContado?.let { BigDecimal(it) },
    diferencia = diferencia?.let { BigDecimal(it) },
)

private fun CorteCaja.toCreateRequestDto() = CorteCajaCreateRequestDto(
    localId = id,
    sucursalId = sucursalId,
    usuarioId = usuarioId,
    tipo = tipo,
    fechaInicio = Instant.ofEpochMilli(fechaInicio).toString(),
    fechaFin = Instant.ofEpochMilli(fechaFin).toString(),
    totalVentas = totalVentas.toPlainString(),
    totalEfectivo = totalEfectivo.toPlainString(),
    totalTarjeta = totalTarjeta.toPlainString(),
    totalRetiros = totalRetiros.toPlainString(),
    montoEsperado = montoEsperado.toPlainString(),
    montoContado = montoContado?.toPlainString(),
    diferencia = diferencia?.toPlainString(),
)
