package com.pdv.pos.sync.push

import com.pdv.pos.data.local.CajaDao
import com.pdv.pos.data.remote.CajaApiService
import com.pdv.pos.sync.toCreateRequestDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CorteCajaPusher @Inject constructor(
    private val dao: CajaDao,
    private val api: CajaApiService,
    private val registroRechazos: RegistroRechazos,
) : EntityPusher {

    override val nombre = "cortes de caja"

    override suspend fun contarPendientes() = dao.contarCortesPendientes()

    override suspend fun contarRechazadas() = dao.contarCortesRechazados()

    override suspend fun empujarPendientes(): PushResultado {
        var subidas = 0
        var saltadas = 0
        for (corte in dao.getCortesPendientes()) {
            val fila = FilaSync("corte_caja", corte.localId, corte.sucursalId, "corte ${corte.tipo} ${corte.localId}")
            val dto = registroRechazos.empujarFila(fila, { codigo -> dao.marcarCorteRechazado(corte.localId, codigo) }) {
                api.createCorte(corte.toCreateRequestDto())
            }
            if (dto == null) {
                saltadas++
                continue
            }
            dao.marcarCorteSincronizado(corte.localId, dto.id)
            subidas++
        }
        return PushResultado(subidas, saltadas)
    }
}
