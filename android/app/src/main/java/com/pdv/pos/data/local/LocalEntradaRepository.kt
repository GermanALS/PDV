package com.pdv.pos.data.local

import com.pdv.pos.domain.model.ArticuloNuevo
import com.pdv.pos.domain.model.Entrada
import com.pdv.pos.domain.repository.EntradaRepository
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TIPO_MOVIMIENTO_ENTRADA = "entrada"
private const val REFERENCIA_ENTRADA_MANUAL = "entrada_manual"

// Articulo (si es nuevo) + inventario + movimiento tipo "entrada" en la
// misma transaccion Room (PLAN.md Parte 8), analogo a como LocalVentaRepository
// (Parte 7) trata la venta como evento aditivo.
@Singleton
class LocalEntradaRepository @Inject constructor(
    private val dao: EntradaDao,
    private val appLogger: AppLogger,
) : EntradaRepository {

    override suspend fun registrarEntrada(entrada: Entrada) {
        val now = System.currentTimeMillis()
        val articuloId = entrada.articuloId()

        dao.insertEntradaCompleta(
            articuloNuevo = (entrada as? Entrada.DeArticuloNuevo)?.articulo?.toEntity(now),
            sucursalId = entrada.sucursalId,
            articuloId = articuloId,
            deltaCantidad = entrada.cantidad,
            ubicacion = entrada.ubicacion,
            movimiento = entrada.toMovimientoEntity(articuloId = articuloId, now = now),
            now = now,
        )

        appLogger.log(
            LogType.DB_WRITE,
            sucursalId = entrada.sucursalId,
            usuario = entrada.usuarioId,
            mensaje = "Entrada registrada: articulo=$articuloId, cantidad=${entrada.cantidad}",
        )
    }
}

private fun Entrada.articuloId(): String = when (this) {
    is Entrada.DeArticuloNuevo -> articulo.id
    is Entrada.DeArticuloExistente -> articuloId
}

private fun ArticuloNuevo.toEntity(now: Long) = ArticuloEntity(
    localId = id,
    remoteId = null,
    codigoBarras = codigoBarras,
    sku = sku,
    nombre = nombre,
    descripcion = descripcion,
    categoria = categoria,
    unidadMedida = unidadMedida,
    precioVenta = precioVenta,
    costo = costo,
    activo = true,
    updatedAt = now,
    isSynced = false,
    deletedAt = null,
)

private fun Entrada.toMovimientoEntity(articuloId: String, now: Long) = MovimientoEntity(
    localId = UUID.randomUUID().toString(),
    remoteId = null,
    sucursalId = sucursalId,
    articuloId = articuloId,
    usuarioId = usuarioId,
    tipo = TIPO_MOVIMIENTO_ENTRADA,
    cantidad = cantidad,
    ubicacion = ubicacion,
    referenciaTipo = REFERENCIA_ENTRADA_MANUAL,
    referenciaId = id,
    fecha = fecha,
    updatedAt = now,
    isSynced = false,
    deletedAt = null,
)
