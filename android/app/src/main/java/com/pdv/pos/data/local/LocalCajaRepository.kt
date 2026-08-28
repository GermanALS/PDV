package com.pdv.pos.data.local

import com.pdv.pos.domain.model.CorteCaja
import com.pdv.pos.domain.model.TotalesCorte
import com.pdv.pos.domain.repository.CajaRepository
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val METODO_PAGO_EFECTIVO = "efectivo"
private const val METODO_PAGO_TARJETA = "tarjeta"

// Cada corte (parcial o final) recalcula sus totales desde `ventas`/
// `retiros_efectivo` filtrando por su propio periodo, en vez de sumar
// cortes anteriores - PLAN.md Parte 10, "Decisiones abiertas".
@Singleton
class LocalCajaRepository @Inject constructor(
    private val cajaDao: CajaDao,
    private val ventaDao: VentaDao,
    private val retiroDao: RetiroDao,
    private val appLogger: AppLogger,
) : CajaRepository {

    override suspend fun calcularTotales(sucursalId: String, fechaInicio: Long, fechaFin: Long): TotalesCorte {
        val ventas = ventaDao.getVentasDelPeriodo(sucursalId, fechaInicio, fechaFin)
        val retiros = retiroDao.getRetirosDelPeriodo(sucursalId, fechaInicio, fechaFin)

        val totalEfectivo = ventas.filter { it.metodoPago == METODO_PAGO_EFECTIVO }.sumOf { it.total }
        val totalTarjeta = ventas.filter { it.metodoPago == METODO_PAGO_TARJETA }.sumOf { it.total }
        val totalRetiros = retiros.sumOf { it.monto }

        return TotalesCorte(
            totalVentas = ventas.sumOf { it.total },
            totalEfectivo = totalEfectivo,
            totalTarjeta = totalTarjeta,
            totalRetiros = totalRetiros,
            montoEsperado = totalEfectivo - totalRetiros,
        )
    }

    override suspend fun guardarCorte(corte: CorteCaja) {
        val now = System.currentTimeMillis()
        cajaDao.insertCorte(corte.toEntity(now))

        appLogger.log(
            LogType.DB_WRITE,
            sucursalId = corte.sucursalId,
            usuario = corte.usuarioId,
            mensaje = "Corte de caja guardado: tipo=${corte.tipo}, total_ventas=${corte.totalVentas}, " +
                "monto_esperado=${corte.montoEsperado}",
        )
    }

    override fun observeCortes(sucursalId: String): Flow<List<CorteCaja>> =
        cajaDao.observarCortes(sucursalId).map { entidades -> entidades.map { it.toDomain() } }
}

private fun CorteCajaEntity.toDomain() = CorteCaja(
    id = localId,
    sucursalId = sucursalId,
    usuarioId = usuarioId,
    tipo = tipo,
    fechaInicio = fechaInicio,
    fechaFin = fechaFin,
    totalVentas = totalVentas,
    totalEfectivo = totalEfectivo,
    totalTarjeta = totalTarjeta,
    totalRetiros = totalRetiros,
    montoEsperado = montoEsperado,
    montoContado = montoContado,
    diferencia = diferencia,
)

private fun CorteCaja.toEntity(now: Long) = CorteCajaEntity(
    localId = id,
    remoteId = null,
    sucursalId = sucursalId,
    usuarioId = usuarioId,
    tipo = tipo,
    fechaInicio = fechaInicio,
    fechaFin = fechaFin,
    totalVentas = totalVentas,
    totalEfectivo = totalEfectivo,
    totalTarjeta = totalTarjeta,
    totalRetiros = totalRetiros,
    montoEsperado = montoEsperado,
    montoContado = montoContado,
    diferencia = diferencia,
    updatedAt = now,
    isSynced = false,
    deletedAt = null,
)
