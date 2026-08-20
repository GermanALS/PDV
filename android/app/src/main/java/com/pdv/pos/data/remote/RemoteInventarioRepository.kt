package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.ArticuloEdicionRequestDto
import com.pdv.pos.data.remote.dto.InventarioItemDto
import com.pdv.pos.domain.model.Articulo
import com.pdv.pos.domain.model.EdicionArticulo
import com.pdv.pos.domain.model.InventarioItem
import com.pdv.pos.domain.model.PaginaInventario
import com.pdv.pos.domain.repository.InventarioRepository
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import java.io.IOException
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

// Las lecturas (observar*) propagan fallas de red por el Flow sin
// capturarlas aca, mismo criterio que RemoteSucursalRepository (Parte 6).
// El logging ERROR/WARN de esta Parte se concentra en actualizarArticulo,
// la unica operacion de escritura y la unica que trae usuarioId para el
// log (AppLogger.log exige sucursalId + usuario, PLAN.md Parte 5).
@Singleton
class RemoteInventarioRepository @Inject constructor(
    private val api: InventarioApiService,
    private val appLogger: AppLogger,
) : InventarioRepository {

    override fun observarInventario(
        sucursalId: String,
        busqueda: String,
        pagina: Int,
        tamanioPagina: Int,
    ): Flow<PaginaInventario> = flow {
        val termino = busqueda.trim().ifBlank { null }
        val respuesta = api.getInventario(sucursalId = sucursalId, q = termino, page = pagina, pageSize = tamanioPagina)
        emit(
            PaginaInventario(
                items = respuesta.items.map { it.toDomain() },
                pagina = respuesta.page,
                tamanioPagina = respuesta.pageSize,
                total = respuesta.total,
            ),
        )
    }

    override fun observarCategorias(): Flow<List<String>> = flow { emit(api.getCategorias().valores) }

    override fun observarUnidadesMedida(): Flow<List<String>> = flow { emit(api.getUnidadesMedida().valores) }

    override fun observarUbicaciones(sucursalId: String): Flow<List<String>> =
        flow { emit(api.getUbicaciones(sucursalId).valores) }

    override suspend fun actualizarArticulo(edicion: EdicionArticulo) {
        try {
            api.ajustarArticulo(edicion.articuloId, edicion.toRequestDto())
        } catch (e: IOException) {
            logFallo(edicion, e)
            throw e
        } catch (e: HttpException) {
            logFallo(edicion, e)
            throw e
        }
    }

    private suspend fun logFallo(edicion: EdicionArticulo, e: Exception) {
        appLogger.log(
            LogType.ERROR,
            sucursalId = edicion.sucursalId,
            usuario = edicion.usuarioId,
            mensaje = "Fallo de red al actualizar articulo: articulo=${edicion.articuloId}, ${e.message}",
        )
    }
}

private fun InventarioItemDto.toDomain() = InventarioItem(
    articulo = Articulo(
        id = articuloId,
        codigoBarras = codigoBarras,
        sku = sku,
        nombre = nombre,
        descripcion = descripcion,
        categoria = categoria,
        unidadMedida = unidadMedida,
        precioVenta = BigDecimal(precioVenta),
        costo = costo?.let { BigDecimal(it) },
    ),
    cantidad = BigDecimal(cantidad),
    ubicacion = ubicacion,
)

private fun EdicionArticulo.toRequestDto() = ArticuloEdicionRequestDto(
    sucursalId = sucursalId,
    usuarioId = usuarioId,
    nombre = nombre,
    descripcion = descripcion,
    categoria = categoria,
    unidadMedida = unidadMedida,
    precioVenta = precioVenta.toPlainString(),
    costo = costo?.toPlainString(),
    cantidad = nuevaCantidad.toPlainString(),
    ubicacion = ubicacion,
)
