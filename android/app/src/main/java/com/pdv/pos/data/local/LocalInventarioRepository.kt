package com.pdv.pos.data.local

import com.pdv.pos.domain.model.Articulo
import com.pdv.pos.domain.model.EdicionArticulo
import com.pdv.pos.domain.model.InventarioItem
import com.pdv.pos.domain.model.PaginaInventario
import com.pdv.pos.domain.repository.InventarioRepository
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalInventarioRepository @Inject constructor(
    private val dao: InventarioDao,
    private val appLogger: AppLogger,
) : InventarioRepository {

    override fun observarInventario(
        sucursalId: String,
        busqueda: String,
        pagina: Int,
        tamanioPagina: Int,
    ): Flow<PaginaInventario> {
        val termino = busqueda.trim()
        val desplazamiento = (pagina - 1).coerceAtLeast(0) * tamanioPagina
        return combine(
            dao.observarPagina(sucursalId, termino, tamanioPagina, desplazamiento),
            dao.observarTotal(sucursalId, termino),
        ) { filas, total ->
            PaginaInventario(
                items = filas.map { it.toDomain() },
                pagina = pagina,
                tamanioPagina = tamanioPagina,
                total = total,
            )
        }
    }

    override suspend fun sumarStock(sucursalId: String, termino: String, categoria: String?): BigDecimal =
        BigDecimal.valueOf(dao.sumarCantidad(sucursalId, termino.trim(), categoria?.trim()?.ifBlank { null }))

    override fun observarInventarioParaExport(
        sucursalId: String,
        busqueda: String,
        pagina: Int,
        tamanioPagina: Int,
    ): Flow<PaginaInventario> {
        val termino = busqueda.trim()
        val desplazamiento = (pagina - 1).coerceAtLeast(0) * tamanioPagina
        return combine(
            dao.observarPaginaExport(sucursalId, termino, tamanioPagina, desplazamiento),
            dao.observarTotal(sucursalId, termino),
        ) { filas, total ->
            PaginaInventario(
                items = filas.map { it.toDomain() },
                pagina = pagina,
                tamanioPagina = tamanioPagina,
                total = total,
            )
        }
    }

    override fun observarCategorias(): Flow<List<String>> = dao.observarCategorias()

    override fun observarUnidadesMedida(): Flow<List<String>> = dao.observarUnidadesMedida()

    override fun observarUbicaciones(sucursalId: String): Flow<List<String>> = dao.observarUbicaciones(sucursalId)

    override suspend fun actualizarArticulo(edicion: EdicionArticulo) {
        val now = System.currentTimeMillis()
        val articuloExistente = dao.getArticulo(edicion.articuloId)
            ?: error("Articulo no encontrado: ${edicion.articuloId}")

        dao.actualizarArticuloCompleto(
            articulo = articuloExistente.copy(
                nombre = edicion.nombre,
                descripcion = edicion.descripcion,
                categoria = edicion.categoria,
                unidadMedida = edicion.unidadMedida,
                precioVenta = edicion.precioVenta,
                costo = edicion.costo,
                updatedAt = now,
                isSynced = false,
            ),
            sucursalId = edicion.sucursalId,
            usuarioId = edicion.usuarioId,
            nuevaCantidad = edicion.nuevaCantidad,
            ubicacion = edicion.ubicacion,
            now = now,
        )

        appLogger.log(
            LogType.DB_WRITE,
            sucursalId = edicion.sucursalId,
            usuario = edicion.usuarioId,
            mensaje = "Articulo actualizado: articulo=${edicion.articuloId}, cantidad=${edicion.nuevaCantidad}",
        )
    }
}

private fun InventarioConArticuloRow.toDomain() = InventarioItem(
    articulo = Articulo(
        id = articuloLocalId,
        codigoBarras = codigoBarras,
        sku = sku,
        nombre = nombre,
        descripcion = descripcion,
        categoria = categoria,
        unidadMedida = unidadMedida,
        precioVenta = precioVenta,
        costo = costo,
        activo = activo,
    ),
    cantidad = cantidad,
    ubicacion = ubicacion,
)
