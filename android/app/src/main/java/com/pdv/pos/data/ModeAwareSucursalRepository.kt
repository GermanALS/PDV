package com.pdv.pos.data

import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.local.LocalSucursalRepository
import com.pdv.pos.data.remote.RemoteSucursalRepository
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.Sucursal
import com.pdv.pos.domain.repository.SucursalRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Resuelve local/remota segun BackendMode en DataStore (PLAN.md Parte 6,
// sub-paso 5 "Wiring"; CLAUDE.md sec. 3). El catalogo de sucursales se
// trae del backend tanto en REMOTO como en LOCAL_CON_SINCRONIZACION,
// alineado con el diseno aprobado en la Parte 3 ("en modo remoto o
// local-con-sincronizacion la trae con GET /sucursales"); solo el modo
// LOCAL puro lee de Room. En LOCAL_CON_SINCRONIZACION la lectura remota es
// best-effort: si el backend falla o rechaza la request, se cae al catalogo
// local (ver el branch correspondiente).
@Singleton
class ModeAwareSucursalRepository @Inject constructor(
    private val local: LocalSucursalRepository,
    private val remote: RemoteSucursalRepository,
    private val preferences: ConfiguracionPreferences,
) : SucursalRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSucursales(): Flow<List<Sucursal>> =
        preferences.deviceConfig
            .map { it.backendMode }
            .distinctUntilChanged()
            .flatMapLatest { modo ->
                when (modo) {
                    BackendMode.LOCAL -> local.observeSucursales()
                    BackendMode.REMOTO -> remote.observeSucursales()
                    // Offline-first: el catalogo remoto es enriquecimiento
                    // best-effort en este modo. Si el backend no responde o
                    // rechaza la request (ej. sin JWT porque el login fue
                    // local, enforcement de la Parte 21), se cae al catalogo
                    // local en vez de propagar la excepcion y crashear la app
                    // en el colector (VentaViewModel.generarTicketSeguro,
                    // ConfiguracionViewModel, EstadoPuntoVenta). El motor de
                    // sync diferido (pendiente) conciliara ambos catalogos.
                    BackendMode.LOCAL_CON_SINCRONIZACION ->
                        remote.observeSucursales().catch { emitAll(local.observeSucursales()) }
                }
            }
}
