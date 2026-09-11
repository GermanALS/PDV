package com.pdv.pos.sync.push

import com.pdv.pos.data.local.CajaDao
import com.pdv.pos.data.remote.CajaApiService
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.sync.toCreateRequestDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CorteCajaPusher @Inject constructor(
    private val dao: CajaDao,
    private val api: CajaApiService,
    private val appLogger: AppLogger,
) : EntityPusher {

    override val nombre = "cortes de caja"

    override suspend fun contarPendientes() = dao.contarCortesPendientes()

    override suspend fun empujarPendientes(): PushResultado {
        var subidas = 0
        var saltadas = 0
        for (corte in dao.getCortesPendientes()) {
            val dto = empujarFila(appLogger, "corte ${corte.tipo} ${corte.localId}") {
                api.createCorte(corte.toCreateRequestDto())
            }
            if (dto == null) {
                // Evita reintentar un corte irrecuperable en cada ciclo
                // (PLAN.md Parte 32, hallazgo de verificacion en dispositivo).
                dao.marcarCorteDescartado(corte.localId)
                saltadas++
                continue
            }
            dao.marcarCorteSincronizado(corte.localId, dto.id)
            subidas++
        }
        return PushResultado(subidas, saltadas)
    }
}
