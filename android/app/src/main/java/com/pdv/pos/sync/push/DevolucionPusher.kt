package com.pdv.pos.sync.push

import com.pdv.pos.data.local.DevolucionDao
import com.pdv.pos.data.remote.DevolucionApiService
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.sync.toCreateRequestDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DevolucionPusher @Inject constructor(
    private val dao: DevolucionDao,
    private val api: DevolucionApiService,
    private val appLogger: AppLogger,
) : EntityPusher {

    override val nombre = "devoluciones"

    override suspend fun contarPendientes() = dao.contarDevolucionesPendientes()

    override suspend fun empujarPendientes(): PushResultado {
        var subidas = 0
        var saltadas = 0
        for (devolucion in dao.getDevolucionesPendientes()) {
            val detalles = dao.getDetallesDeDevolucion(devolucion.localId)
            // Mismo motivo que VentaPusher: cada linea referencia el
            // articulo por su id LOCAL, y ventaId (si existe) tambien es
            // local. Si algo todavia no sincronizo, la devolucion se aplaza
            // entera en vez de mandar un id local que el backend no conoce
            // (PLAN.md Parte 32, hallazgo de verificacion en dispositivo).
            val articulosRemotos = detalles.associate { it.articuloId to dao.getArticulo(it.articuloId)?.remoteId }
            val ventaIdLocal = devolucion.ventaId
            val ventaIdRemoto = ventaIdLocal?.let { dao.getVentaRemoteId(it) }
            val ventaSinResolver = ventaIdLocal != null && ventaIdRemoto == null
            if (articulosRemotos.values.any { it == null } || ventaSinResolver) {
                saltadas++
                continue
            }

            val dto = empujarFila(appLogger, "devolucion ${devolucion.folio}") {
                api.createDevolucion(
                    devolucion.toCreateRequestDto(detalles, ventaIdRemoto) { local -> articulosRemotos.getValue(local)!! },
                )
            }
            if (dto == null) {
                dao.marcarDevolucionDescartada(devolucion.localId)
                saltadas++
                continue
            }
            dao.marcarDevolucionSincronizada(devolucion.localId, dto.id)
            dao.marcarDetallesDevolucionSincronizados(devolucion.localId)
            subidas++
        }
        return PushResultado(subidas, saltadas)
    }
}
