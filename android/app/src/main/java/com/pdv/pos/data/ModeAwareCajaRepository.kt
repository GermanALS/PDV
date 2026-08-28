package com.pdv.pos.data

import com.pdv.pos.caja.CajaRefreshSignal
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.local.LocalCajaRepository
import com.pdv.pos.data.remote.RemoteCajaRepository
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.CorteCaja
import com.pdv.pos.domain.model.TotalesCorte
import com.pdv.pos.domain.repository.CajaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Resuelve local/remota segun BackendMode en DataStore, mismo criterio que
// ModeAwareVentaRepository (PLAN.md Parte 10, sub-paso 4): "local con
// sincronizacion" calcula/escribe en Room igual que "local" - el disparo
// del sync en background hacia el remoto es infraestructura aparte.
@Singleton
class ModeAwareCajaRepository @Inject constructor(
    private val local: LocalCajaRepository,
    private val remote: RemoteCajaRepository,
    private val preferences: ConfiguracionPreferences,
    private val cajaRefreshSignal: CajaRefreshSignal,
) : CajaRepository {

    override suspend fun calcularTotales(sucursalId: String, fechaInicio: Long, fechaFin: Long): TotalesCorte =
        when (preferences.deviceConfig.first().backendMode) {
            BackendMode.LOCAL, BackendMode.LOCAL_CON_SINCRONIZACION ->
                local.calcularTotales(sucursalId, fechaInicio, fechaFin)
            BackendMode.REMOTO -> remote.calcularTotales(sucursalId, fechaInicio, fechaFin)
        }

    override suspend fun guardarCorte(corte: CorteCaja) {
        when (preferences.deviceConfig.first().backendMode) {
            BackendMode.LOCAL, BackendMode.LOCAL_CON_SINCRONIZACION -> local.guardarCorte(corte)
            BackendMode.REMOTO -> remote.guardarCorte(corte)
        }
        // Unico punto de escritura de Caja (pantalla manual y corte_parcial
        // de la IA pasan ambos por aca) - PLAN.md Parte 18, sub-parte F.
        cajaRefreshSignal.emitir()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCortes(sucursalId: String): Flow<List<CorteCaja>> =
        preferences.deviceConfig.map { it.backendMode }.distinctUntilChanged().flatMapLatest { modo ->
            when (modo) {
                BackendMode.LOCAL, BackendMode.LOCAL_CON_SINCRONIZACION -> local.observeCortes(sucursalId)
                BackendMode.REMOTO -> remote.observeCortes(sucursalId)
            }
        }
}
