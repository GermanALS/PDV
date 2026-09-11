package com.pdv.pos.sync.push

import com.pdv.pos.data.local.EntradaDao
import com.pdv.pos.data.remote.EntradaApiService
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.sync.toEntradaCreateRequestDto
import javax.inject.Inject
import javax.inject.Singleton

// No hay entidad "entrada": la unidad a subir es el movimiento tipo "entrada"
// (PLAN.md Parte 32, Grupo 2). Si el articulo referido todavia no esta en el
// backend (remoteId nulo) se envia como articulo_nuevo y al volver se
// persiste su remoteId, para que las entradas/ventas/ajustes siguientes lo
// referencien por id remoto.
@Singleton
class EntradaPusher @Inject constructor(
    private val dao: EntradaDao,
    private val api: EntradaApiService,
    private val appLogger: AppLogger,
) : EntityPusher {

    override val nombre = "entradas"

    override suspend fun contarPendientes() = dao.contarEntradasPendientes()

    override suspend fun empujarPendientes(): PushResultado {
        var subidas = 0
        var saltadas = 0
        for (mov in dao.getMovimientosEntradaPendientes()) {
            val articulo = dao.getArticulo(mov.articuloId)
            val articuloNuevo = articulo?.takeIf { it.remoteId == null }
            val dto = empujarFila(appLogger, "entrada ${mov.referenciaId ?: mov.localId}") {
                api.createEntrada(mov.toEntradaCreateRequestDto(articulo?.remoteId, articuloNuevo))
            }
            if (dto == null) {
                // Evita reintentar una entrada irrecuperable en cada ciclo
                // (PLAN.md Parte 32, hallazgo de verificacion en dispositivo).
                dao.marcarMovimientoDescartado(mov.localId)
                saltadas++
                continue
            }
            dao.marcarMovimientoSincronizado(mov.localId, dto.movimiento.id)
            dto.articulo?.let { dao.marcarArticuloSincronizado(mov.articuloId, it.id) }
            subidas++
        }
        return PushResultado(subidas, saltadas)
    }
}
