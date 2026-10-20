package com.pdv.pos.sync.push

import com.pdv.pos.data.local.InventarioDao
import com.pdv.pos.data.remote.InventarioApiService
import com.pdv.pos.data.remote.dto.AjusteDeltaRequestDto
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

// Sube los ajustes manuales de inventario creados offline (PLAN.md Parte 32,
// Grupo 2, sub-paso 7b; rehecho en la Parte 34, A-2). Cada movimiento tipo
// "ajuste" pendiente se envia como un delta con signo a POST
// /ajustes-inventario, idempotente por el localId del movimiento: asi el
// ajuste se suma a lo que otras terminales hayan vendido o ingresado, en vez
// de imponer la cantidad absoluta local. Si el articulo todavia no tiene
// remoteId (su entrada aun no sincronizo) el movimiento se salta y se
// reintenta el proximo ciclo.
//
// Este endpoint solo suma stock: las ediciones de catalogo y de ubicacion las
// sube ArticuloEdicionPusher (PLAN.md Parte 35).
@Singleton
class InventarioAjustePusher @Inject constructor(
    private val dao: InventarioDao,
    private val api: InventarioApiService,
    private val appLogger: AppLogger,
    private val registroRechazos: RegistroRechazos,
) : EntityPusher {

    override val nombre = "ajustes de inventario"

    override suspend fun contarPendientes() = dao.contarAjustesPendientes()

    override suspend fun contarRechazadas() = dao.contarAjustesRechazados()

    override suspend fun empujarPendientes(): PushResultado {
        var subidas = 0
        var saltadas = 0

        for (mov in dao.getMovimientosAjustePendientes()) {
            val articuloRemotoId = dao.getArticulo(mov.articuloId)?.remoteId
            if (articuloRemotoId == null) {
                appLogger.log(
                    LogType.WARN,
                    sucursalId = mov.sucursalId,
                    usuario = "system",
                    mensaje = "Sync: ajuste de ${mov.articuloId} aplazado (articulo aun sin sincronizar)",
                )
                saltadas++
                continue
            }

            val fila = FilaSync("movimiento", mov.localId, mov.sucursalId, "ajuste ${mov.localId}")
            val dto = registroRechazos.empujarFila(fila, { codigo -> dao.marcarMovimientoRechazado(mov.localId, codigo) }) {
                api.ajustarPorDelta(
                    AjusteDeltaRequestDto(
                        localId = mov.localId,
                        sucursalId = mov.sucursalId,
                        articuloId = articuloRemotoId,
                        usuarioId = mov.usuarioId,
                        fecha = Instant.ofEpochMilli(mov.fecha).toString(),
                        delta = mov.cantidad.toPlainString(),
                    ),
                )
            }
            if (dto == null) {
                // Rechazado: no se reintenta - a diferencia del salto de
                // arriba (articulo aun sin remoteId), que es temporal y se
                // reintenta a proposito.
                saltadas++
                continue
            }

            dao.marcarMovimientoSincronizado(mov.localId, dto.movimiento.id)
            subidas++
        }
        return PushResultado(subidas, saltadas)
    }
}
