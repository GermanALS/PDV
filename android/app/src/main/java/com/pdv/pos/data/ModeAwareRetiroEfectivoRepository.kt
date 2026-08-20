package com.pdv.pos.data

import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.local.LocalRetiroEfectivoRepository
import com.pdv.pos.data.remote.RemoteRetiroEfectivoRepository
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.RetiroEfectivo
import com.pdv.pos.domain.repository.RetiroEfectivoRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

// Resuelve local/remota segun BackendMode en DataStore, mismo criterio que
// ModeAwareVentaRepository (PLAN.md Parte 10, sub-paso 4).
@Singleton
class ModeAwareRetiroEfectivoRepository @Inject constructor(
    private val local: LocalRetiroEfectivoRepository,
    private val remote: RemoteRetiroEfectivoRepository,
    private val preferences: ConfiguracionPreferences,
) : RetiroEfectivoRepository {

    override suspend fun registrarRetiro(retiro: RetiroEfectivo) {
        when (preferences.deviceConfig.first().backendMode) {
            BackendMode.LOCAL, BackendMode.LOCAL_CON_SINCRONIZACION -> local.registrarRetiro(retiro)
            BackendMode.REMOTO -> remote.registrarRetiro(retiro)
        }
    }
}
