package com.pdv.pos.sync.push

import com.pdv.pos.data.local.RetiroDao
import com.pdv.pos.data.remote.RetiroApiService
import com.pdv.pos.sync.toCreateRequestDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetiroPusher @Inject constructor(
    private val dao: RetiroDao,
    private val api: RetiroApiService,
    private val registroRechazos: RegistroRechazos,
) : EntityPusher {

    override val nombre = "retiros de efectivo"

    override suspend fun contarPendientes() = dao.contarRetirosPendientes()

    override suspend fun contarRechazadas() = dao.contarRetirosRechazados()

    override suspend fun empujarPendientes(): PushResultado {
        var subidas = 0
        var saltadas = 0
        for (retiro in dao.getRetirosPendientes()) {
            val fila = FilaSync("retiro_efectivo", retiro.localId, retiro.sucursalId, "retiro ${retiro.localId}")
            val dto = registroRechazos.empujarFila(fila, { codigo -> dao.marcarRetiroRechazado(retiro.localId, codigo) }) {
                api.createRetiro(retiro.toCreateRequestDto())
            }
            if (dto == null) {
                saltadas++
                continue
            }
            dao.marcarRetiroSincronizado(retiro.localId, dto.id)
            subidas++
        }
        return PushResultado(subidas, saltadas)
    }
}
