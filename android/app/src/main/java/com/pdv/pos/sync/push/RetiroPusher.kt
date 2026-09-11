package com.pdv.pos.sync.push

import com.pdv.pos.data.local.RetiroDao
import com.pdv.pos.data.remote.RetiroApiService
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.sync.toCreateRequestDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetiroPusher @Inject constructor(
    private val dao: RetiroDao,
    private val api: RetiroApiService,
    private val appLogger: AppLogger,
) : EntityPusher {

    override val nombre = "retiros de efectivo"

    override suspend fun contarPendientes() = dao.contarRetirosPendientes()

    override suspend fun empujarPendientes(): PushResultado {
        var subidas = 0
        var saltadas = 0
        for (retiro in dao.getRetirosPendientes()) {
            val dto = empujarFila(appLogger, "retiro ${retiro.localId}") {
                api.createRetiro(retiro.toCreateRequestDto())
            }
            if (dto == null) {
                // Evita reintentar un retiro irrecuperable en cada ciclo
                // (PLAN.md Parte 32, hallazgo de verificacion en dispositivo).
                dao.marcarRetiroDescartado(retiro.localId)
                saltadas++
                continue
            }
            dao.marcarRetiroSincronizado(retiro.localId, dto.id)
            subidas++
        }
        return PushResultado(subidas, saltadas)
    }
}
