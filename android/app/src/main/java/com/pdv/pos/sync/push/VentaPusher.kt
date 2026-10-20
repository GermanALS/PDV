package com.pdv.pos.sync.push

import com.pdv.pos.data.local.VentaDao
import com.pdv.pos.data.remote.VentaApiService
import com.pdv.pos.sync.toCreateRequestDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VentaPusher @Inject constructor(
    private val dao: VentaDao,
    private val api: VentaApiService,
    private val registroRechazos: RegistroRechazos,
) : EntityPusher {

    override val nombre = "ventas"

    override suspend fun contarPendientes() = dao.contarVentasPendientes()

    override suspend fun contarRechazadas() = dao.contarVentasRechazadas()

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

            val fila = FilaSync("venta", venta.localId, venta.sucursalId, "venta ${venta.folio}")
            val dto = registroRechazos.empujarFila(
                fila,
                marcarRechazada = { codigo ->
                    // Sus movimientos tambien se marcan: si quedaran
                    // pendientes, el pull de inventario no convergeria nunca
                    // para ese articulo (PLAN.md Parte 34).
                    dao.marcarVentaRechazada(venta.localId, codigo)
                    dao.marcarMovimientosVentaSincronizados(venta.localId)
                },
            ) {
                api.createVenta(venta.toCreateRequestDto(detalles) { local -> articulosRemotos.getValue(local)!! })
            }
            if (dto == null) {
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
