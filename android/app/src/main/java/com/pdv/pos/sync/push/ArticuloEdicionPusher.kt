package com.pdv.pos.sync.push

import com.pdv.pos.data.local.ArticuloEntity
import com.pdv.pos.data.local.InventarioDao
import com.pdv.pos.data.local.InventarioEntity
import com.pdv.pos.data.remote.InventarioApiService
import com.pdv.pos.data.remote.dto.ArticuloCatalogoDto
import com.pdv.pos.data.remote.dto.ArticuloCatalogoRequestDto
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import com.pdv.pos.sync.EventoAditivoSyncEngine
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

// Sube las ediciones de catalogo y ubicacion hechas offline (PLAN.md Parte
// 35). La unidad es la fila de inventario con edicionPendiente: se envia a
// PATCH /articulos/{id}, que nunca toca la cantidad. El servidor resuelve
// last-write-wins por editado_en; si la edicion local pierde (aplicado =
// false) se adopta lo vigente del backend y se registra el conflicto. Un
// articulo sin remoteId (su entrada aun no sincroniza) se salta: esa entrada
// ya lleva los atributos actuales y la edicion se sube el ciclo siguiente.
@Singleton
class ArticuloEdicionPusher @Inject constructor(
    private val dao: InventarioDao,
    private val api: InventarioApiService,
    private val eventoAditivoSyncEngine: EventoAditivoSyncEngine,
    private val appLogger: AppLogger,
    private val registroRechazos: RegistroRechazos,
) : EntityPusher {

    override val nombre = "ediciones de catalogo"

    override suspend fun contarPendientes() = dao.contarEdicionesPendientes()

    override suspend fun contarRechazadas() = dao.contarEdicionesRechazadas()

    override suspend fun empujarPendientes(): PushResultado {
        var subidas = 0
        var saltadas = 0

        for (inventario in dao.getEdicionesPendientes()) {
            val articulo = dao.getArticulo(inventario.articuloId)
            val articuloRemotoId = articulo?.remoteId
            if (articulo == null || articuloRemotoId == null) {
                appLogger.log(
                    LogType.WARN,
                    sucursalId = inventario.sucursalId,
                    usuario = "system",
                    mensaje = "Sync: edicion de ${inventario.articuloId} aplazada (articulo aun sin sincronizar)",
                )
                saltadas++
                continue
            }

            val fila = FilaSync("articulo", articulo.localId, inventario.sucursalId, "edicion de catalogo ${articulo.nombre}")
            val marcarRechazada: suspend (Int) -> Unit = { codigo ->
                // Se cierra para no reintentarla en cada ciclo y para que el
                // pull vuelva a converger al catalogo del backend.
                dao.cerrarEdicionSubida(inventario.localId, articulo.localId, articulo.updatedAt, null, null)
                dao.marcarRechazoEdicion(inventario.localId, codigo)
            }
            val dto = registroRechazos.empujarFila(fila, marcarRechazada) {
                api.editarCatalogo(articuloRemotoId, solicitud(articulo, inventario))
            }
            if (dto == null) {
                saltadas++
                continue
            }
            dao.marcarRechazoEdicion(inventario.localId, null)

            if (dto.aplicado) {
                dao.cerrarEdicionSubida(inventario.localId, articulo.localId, articulo.updatedAt, null, null)
            } else {
                registrarPerdida(articulo, inventario, dto)
                dao.cerrarEdicionSubida(
                    inventarioLocalId = inventario.localId,
                    articuloLocalId = articulo.localId,
                    editadoEnLeido = articulo.updatedAt,
                    vigente = vigente(articulo, dto),
                    ubicacionVigente = dto.inventario?.ubicacion ?: inventario.ubicacion,
                )
            }
            subidas++
        }
        return PushResultado(subidas, saltadas)
    }

    private fun solicitud(articulo: ArticuloEntity, inventario: InventarioEntity) = ArticuloCatalogoRequestDto(
        sucursalId = inventario.sucursalId,
        nombre = articulo.nombre,
        descripcion = articulo.descripcion,
        categoria = articulo.categoria,
        unidadMedida = articulo.unidadMedida,
        precioVenta = articulo.precioVenta.toPlainString(),
        costo = articulo.costo?.toPlainString(),
        ubicacion = inventario.ubicacion,
        editadoEn = Instant.ofEpochMilli(articulo.updatedAt).toString(),
    )

    private suspend fun registrarPerdida(articulo: ArticuloEntity, inventario: InventarioEntity, dto: ArticuloCatalogoDto) {
        eventoAditivoSyncEngine.registrarEdicionCatalogoPerdida(
            articuloLocalId = articulo.localId,
            sucursalId = inventario.sucursalId,
            usuario = "system",
            resumenLocal = resumen(articulo.nombre, articulo.precioVenta, inventario.ubicacion),
            resumenRemoto = resumen(
                dto.articulo.nombre,
                BigDecimal(dto.articulo.precioVenta),
                dto.inventario?.ubicacion ?: inventario.ubicacion,
            ),
        )
    }

    private fun vigente(articulo: ArticuloEntity, dto: ArticuloCatalogoDto): ArticuloEntity {
        val remoto = dto.articulo
        return articulo.copy(
            nombre = remoto.nombre,
            descripcion = remoto.descripcion,
            categoria = remoto.categoria,
            unidadMedida = remoto.unidadMedida,
            precioVenta = BigDecimal(remoto.precioVenta),
            costo = remoto.costo?.let(::BigDecimal),
            updatedAt = Instant.parse(remoto.updatedAt).toEpochMilli(),
        )
    }

    private fun resumen(nombre: String, precio: BigDecimal, ubicacion: String?) =
        "nombre=$nombre precio=${precio.toPlainString()} ubicacion=${ubicacion ?: "-"}"
}
