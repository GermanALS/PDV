package com.pdv.pos.data

import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.local.LocalInventarioRepository
import com.pdv.pos.data.remote.RemoteInventarioRepository
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.EdicionArticulo
import com.pdv.pos.domain.model.PaginaInventario
import com.pdv.pos.domain.repository.InventarioRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Resuelve local/remota segun BackendMode en DataStore, mismo criterio que
// ModeAwareSucursalRepository (lecturas reactivas via flatMapLatest) y
// ModeAwareEntradaRepository (escritura via snapshot suspend) combinados,
// porque InventarioRepository tiene ambos tipos de metodo (PLAN.md Parte 9,
// sub-paso 4).
@Singleton
class ModeAwareInventarioRepository @Inject constructor(
    private val local: LocalInventarioRepository,
    private val remote: RemoteInventarioRepository,
    private val preferences: ConfiguracionPreferences,
) : InventarioRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observarInventario(
        sucursalId: String,
        busqueda: String,
        pagina: Int,
        tamanioPagina: Int,
    ): Flow<PaginaInventario> = modo().flatMapLatest { modo ->
        when (modo) {
            BackendMode.LOCAL, BackendMode.LOCAL_CON_SINCRONIZACION ->
                local.observarInventario(sucursalId, busqueda, pagina, tamanioPagina)
            BackendMode.REMOTO -> remote.observarInventario(sucursalId, busqueda, pagina, tamanioPagina)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observarCategorias(): Flow<List<String>> = modo().flatMapLatest { modo ->
        when (modo) {
            BackendMode.LOCAL, BackendMode.LOCAL_CON_SINCRONIZACION -> local.observarCategorias()
            BackendMode.REMOTO -> remote.observarCategorias()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observarUnidadesMedida(): Flow<List<String>> = modo().flatMapLatest { modo ->
        when (modo) {
            BackendMode.LOCAL, BackendMode.LOCAL_CON_SINCRONIZACION -> local.observarUnidadesMedida()
            BackendMode.REMOTO -> remote.observarUnidadesMedida()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observarUbicaciones(sucursalId: String): Flow<List<String>> = modo().flatMapLatest { modo ->
        when (modo) {
            BackendMode.LOCAL, BackendMode.LOCAL_CON_SINCRONIZACION -> local.observarUbicaciones(sucursalId)
            BackendMode.REMOTO -> remote.observarUbicaciones(sucursalId)
        }
    }

    override suspend fun actualizarArticulo(edicion: EdicionArticulo) {
        when (preferences.deviceConfig.first().backendMode) {
            BackendMode.LOCAL, BackendMode.LOCAL_CON_SINCRONIZACION -> local.actualizarArticulo(edicion)
            BackendMode.REMOTO -> remote.actualizarArticulo(edicion)
        }
    }

    private fun modo() = preferences.deviceConfig.map { it.backendMode }.distinctUntilChanged()
}
