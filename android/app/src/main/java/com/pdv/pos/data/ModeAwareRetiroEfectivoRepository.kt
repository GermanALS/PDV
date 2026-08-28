package com.pdv.pos.data

import com.pdv.pos.caja.CajaRefreshSignal
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.local.LocalRetiroEfectivoRepository
import com.pdv.pos.data.remote.RemoteRetiroEfectivoRepository
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.RetiroEfectivo
import com.pdv.pos.domain.repository.RetiroEfectivoRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Resuelve local/remota segun BackendMode en DataStore, mismo criterio que
// ModeAwareVentaRepository (PLAN.md Parte 10, sub-paso 4).
@Singleton
class ModeAwareRetiroEfectivoRepository @Inject constructor(
    private val local: LocalRetiroEfectivoRepository,
    private val remote: RemoteRetiroEfectivoRepository,
    private val preferences: ConfiguracionPreferences,
    private val cajaRefreshSignal: CajaRefreshSignal,
) : RetiroEfectivoRepository {

    override suspend fun registrarRetiro(retiro: RetiroEfectivo) {
        when (preferences.deviceConfig.first().backendMode) {
            BackendMode.LOCAL, BackendMode.LOCAL_CON_SINCRONIZACION -> local.registrarRetiro(retiro)
            BackendMode.REMOTO -> remote.registrarRetiro(retiro)
        }
        // Mismo CajaRefreshSignal que ModeAwareCajaRepository (PLAN.md
        // Parte 18, sub-parte F): CajaViewModel observa cortes y retiros
        // con la misma senial combinada.
        cajaRefreshSignal.emitir()
    }

    override suspend fun obtenerRetirosDelPeriodo(sucursalId: String, desde: Long, hasta: Long): List<RetiroEfectivo> =
        when (preferences.deviceConfig.first().backendMode) {
            BackendMode.LOCAL, BackendMode.LOCAL_CON_SINCRONIZACION ->
                local.obtenerRetirosDelPeriodo(sucursalId, desde, hasta)
            BackendMode.REMOTO -> remote.obtenerRetirosDelPeriodo(sucursalId, desde, hasta)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeRetiros(sucursalId: String): Flow<List<RetiroEfectivo>> =
        preferences.deviceConfig.map { it.backendMode }.distinctUntilChanged().flatMapLatest { modo ->
            when (modo) {
                BackendMode.LOCAL, BackendMode.LOCAL_CON_SINCRONIZACION -> local.observeRetiros(sucursalId)
                BackendMode.REMOTO -> remote.observeRetiros(sucursalId)
            }
        }
}
