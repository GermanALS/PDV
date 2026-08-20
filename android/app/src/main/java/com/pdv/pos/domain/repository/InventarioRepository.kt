package com.pdv.pos.domain.repository

import com.pdv.pos.domain.model.EdicionArticulo
import com.pdv.pos.domain.model.PaginaInventario
import kotlinx.coroutines.flow.Flow

interface InventarioRepository {
    fun observarInventario(
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
