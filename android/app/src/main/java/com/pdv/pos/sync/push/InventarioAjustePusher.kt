package com.pdv.pos.sync.push

import com.pdv.pos.data.local.ArticuloEntity
import com.pdv.pos.data.local.InventarioDao
import com.pdv.pos.data.local.InventarioEntity
import com.pdv.pos.data.remote.InventarioApiService
import com.pdv.pos.data.remote.dto.ArticuloEdicionRequestDto
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import javax.inject.Inject
import javax.inject.Singleton

// Sube los ajustes manuales de inventario creados offline (PLAN.md Parte 32,
// Grupo 2, sub-paso 7b). Los movimientos tipo "ajuste" pendientes se agrupan
// por (sucursal, articulo) y cada grupo se resuelve con un unico
// PATCH /inventario/{articulo_remoto}: el cuerpo lleva la cantidad LOCAL
// actual (nuevo valor) y el backend calcula su propio delta. Si el articulo
// todavia no tiene remoteId (su entrada aun no sincronizo) el grupo se salta
// y se reintenta el proximo ciclo.
//
// Fuera de alcance por ahora: ediciones de catalogo sin cambio de stock
// (delta 0, sin movimiento) - no dejan un pendiente que este pusher pueda
// enganchar. Follow-up.
@Singleton
class InventarioAjustePusher @Inject constructor(
    private val dao: InventarioDao,
    private val api: InventarioApiService,
    private val appLogger: AppLogger,
) : EntityPusher {

    override val nombre = "ajustes de inventario"

    override suspend fun contarPendientes() = dao.contarAjustesPendientes()

    override suspend fun empujarPendientes(): PushResultado {
        var subidas = 0
        var saltadas = 0
        val grupos = dao.getMovimientosAjustePendientes().groupBy { it.sucursalId to it.articuloId }

        for ((clave, movimientos) in grupos) {
            val (sucursalId, articuloId) = clave
            val articulo = dao.getArticulo(articuloId)
            val inventario = dao.getInventario(sucursalId, articuloId)
            if (articulo?.remoteId == null || inventario == null) {
                appLogger.log(
                    LogType.WARN,
                    sucursalId = sucursalId,
                    usuario = "system",
                    mensaje = "Sync: ajuste de $articuloId aplazado (articulo aun sin sincronizar)",
                )
                saltadas += movimientos.size
                continue
            }

            val dto = empujarFila(appLogger, "ajuste de $articuloId") {
                api.ajustarArticulo(articulo.remoteId, request(sucursalId, articulo, inventario, movimientos.first().usuarioId))
            }
            if (dto == null) {
                // Evita reintentar un ajuste irrecuperable en cada ciclo
                // (PLAN.md Parte 32, hallazgo de verificacion en
                // dispositivo) - a diferencia del salto de arriba (articulo
                // aun sin remoteId), que es temporal y se reintenta a
                // proposito.
                movimientos.forEach { mov -> dao.marcarMovimientoSincronizadoSinRemoto(mov.localId) }
                saltadas += movimientos.size
                continue
            }

            movimientos.forEach { mov ->
                val remotoId = dto.movimiento?.id
                if (remotoId != null) dao.marcarMovimientoSincronizado(mov.localId, remotoId)
                else dao.marcarMovimientoSincronizadoSinRemoto(mov.localId)
            }
            dao.marcarArticuloSincronizado(articuloId, dto.articulo.id)
            dao.marcarInventarioSincronizado(inventario.localId, dto.inventario.id)
            subidas += movimientos.size
        }
        return PushResultado(subidas, saltadas)
    }

    private fun request(
        sucursalId: String,
        articulo: ArticuloEntity,
        inventario: InventarioEntity,
        usuarioId: String,
    ) = ArticuloEdicionRequestDto(
        sucursalId = sucursalId,
        usuarioId = usuarioId,
        nombre = articulo.nombre,
        descripcion = articulo.descripcion,
        categoria = articulo.categoria,
        unidadMedida = articulo.unidadMedida,
        precioVenta = articulo.precioVenta.toPlainString(),
        costo = articulo.costo?.toPlainString(),
        cantidad = inventario.cantidad.toPlainString(),
        ubicacion = inventario.ubicacion,
    )
}
