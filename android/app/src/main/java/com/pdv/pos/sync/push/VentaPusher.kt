package com.pdv.pos.sync.push

import com.pdv.pos.data.local.VentaDao
import com.pdv.pos.data.remote.VentaApiService
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.sync.toCreateRequestDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VentaPusher @Inject constructor(
    private val dao: VentaDao,
    private val api: VentaApiService,
    private val appLogger: AppLogger,
) : EntityPusher {

    override val nombre = "ventas"

    override suspend fun contarPendientes() = dao.contarVentasPendientes()

    override suspend fun empujarPendientes(): PushResultado {
        var subidas = 0
        var saltadas = 0
        for (venta in dao.getVentasPendientes()) {
            val detalles = dao.getDetallesDeVenta(venta.localId)
            // Cada linea referencia el articulo por su id LOCAL; el backend
            // solo conoce el suyo. Si algun articulo de la venta todavia no
            // tiene remoteId (su entrada no sincronizo todavia), la venta se
            // aplaza entera - mandar el id local produciria un 404
            // permanente (PLAN.md Parte 32, hallazgo de verificacion en
            // dispositivo).
            val articulosRemotos = detalles.associate { it.articuloId to dao.getArticulo(it.articuloId)?.remoteId }
            if (articulosRemotos.values.any { it == null }) {
                saltadas++
                continue
            }

            val dto = empujarFila(appLogger, "venta ${venta.folio}") {
                api.createVenta(venta.toCreateRequestDto(detalles) { local -> articulosRemotos.getValue(local)!! })
            }
            if (dto == null) {
                // Irrecuperable (4xx no-401): se descarta para no
                // reintentarla en cada ciclo.
                dao.marcarVentaDescartada(venta.localId)
                saltadas++
                continue
            }
            dao.marcarVentaSincronizada(venta.localId, dto.id)
            dao.marcarDetallesVentaSincronizados(venta.localId)
            dao.marcarMovimientosVentaSincronizados(venta.localId)
            subidas++
        }
        return PushResultado(subidas, saltadas)
    }
}
