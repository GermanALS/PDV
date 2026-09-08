package com.pdv.pos.domain.repository

import com.pdv.pos.domain.model.EdicionArticulo
import com.pdv.pos.domain.model.PaginaInventario
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface InventarioRepository {
    fun observarInventario(
        sucursalId: String,
        busqueda: String,
        pagina: Int,
        tamanioPagina: Int,
    ): Flow<PaginaInventario>

    // Suma de existencias agregada en la fuente de datos (M-9): no enumera
    // el catalogo. termino = "" desactiva la busqueda por texto; categoria =
    // null desactiva el filtro por categoria.
    suspend fun sumarStock(sucursalId: String, termino: String, categoria: String?): BigDecimal

    // Como observarInventario pero ordenado por cantidad numerica - lo usa
    // el camino de exportacion (M-9), que de todas formas enumera todas las
    // filas que coinciden para armar el archivo.
    fun observarInventarioParaExport(
        sucursalId: String,
        busqueda: String,
        pagina: Int,
        tamanioPagina: Int,
    ): Flow<PaginaInventario>

    fun observarCategorias(): Flow<List<String>>
    fun observarUnidadesMedida(): Flow<List<String>>
    fun observarUbicaciones(sucursalId: String): Flow<List<String>>

    suspend fun actualizarArticulo(edicion: EdicionArticulo)
}
