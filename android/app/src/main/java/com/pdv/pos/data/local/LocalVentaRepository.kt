package com.pdv.pos.data.local

import com.pdv.pos.domain.model.Venta
import com.pdv.pos.domain.model.VentaLinea
import com.pdv.pos.domain.repository.VentaRepository
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TIPO_MOVIMIENTO_SALIDA = "salida"
private const val REFERENCIA_VENTA = "venta"

// Cada linea de venta genera su propio movimiento de salida (delta negativo
// de inventario.cantidad, PLAN.md Parte 6) en la misma transaccion Room que
// la venta - PLAN.md Parte 7, "Decisiones abiertas" (venta como evento
// aditivo).
@Singleton
class LocalVentaRepository @Inject constructor(
    private val dao: VentaDao,
    private val appLogger: AppLogger,
) : VentaRepository {

    override suspend fun registrarVenta(venta: Venta) {
        val now = System.currentTimeMillis()
        dao.insertVentaCompleta(
            venta = venta.toEntity(now),
            detalles = venta.lineas.map { it.toEntity(ventaId = venta.id, now = now) },
            movimientos = venta.lineas.map { it.toMovimientoEntity(venta = venta, now = now) },
            now = now,
        )

        appLogger.log(
            LogType.DB_WRITE,
            sucursalId = venta.sucursalId,
            usuario = venta.usuarioId,
            mensaje = "Venta registrada: folio=${venta.folio}, total=${venta.total}, lineas=${venta.lineas.size}",
        )
    }
}

private fun Venta.toEntity(now: Long) = VentaEntity(
    localId = id,
    remoteId = null,
    sucursalId = sucursalId,
    usuarioId = usuarioId,
    folio = folio,
    fecha = fecha,
    subtotal = subtotal,
    descuento = descuento,
    impuestos = impuestos,
    total = total,
    metodoPago = metodoPago,
    estado = estado,
    updatedAt = now,
    isSynced = false,
    deletedAt = null,
)

private fun VentaLinea.toEntity(ventaId: String, now: Long) = VentaDetalleEntity(
    localId = UUID.randomUUID().toString(),
    remoteId = null,
    ventaId = ventaId,
    articuloId = articuloId,
    cantidad = cantidad,
    precioUnitario = precioUnitario,
    subtotal = subtotal,
    updatedAt = now,
    isSynced = false,
    deletedAt = null,
)

private fun VentaLinea.toMovimientoEntity(venta: Venta, now: Long) = MovimientoEntity(
    localId = UUID.randomUUID().toString(),
    remoteId = null,
    sucursalId = venta.sucursalId,
    articuloId = articuloId,
    usuarioId = venta.usuarioId,
    tipo = TIPO_MOVIMIENTO_SALIDA,
    cantidad = cantidad,
    ubicacion = null,
    referenciaTipo = REFERENCIA_VENTA,
    referenciaId = venta.id,
    fecha = venta.fecha,
    updatedAt = now,
    isSynced = false,
    deletedAt = null,
)
