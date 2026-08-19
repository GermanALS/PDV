package com.pdv.pos.data

import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.local.LocalEntradaRepository
import com.pdv.pos.data.remote.RemoteEntradaRepository
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.Entrada
import com.pdv.pos.domain.repository.EntradaRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

// Resuelve local/remota segun BackendMode en DataStore, mismo criterio que
// ModeAwareVentaRepository (PLAN.md Parte 7, sub-paso 4): "local con
// sincronizacion" escribe en Room igual que "local".
@Singleton
class ModeAwareEntradaRepository @Inject constructor(
    private val local: LocalEntradaRepository,
    private val remote: RemoteEntradaRepository,
    private val preferences: ConfiguracionPreferences,
) : EntradaRepository {

    override suspend fun registrarEntrada(entrada: Entrada) {
        when (preferences.deviceConfig.first().backendMode) {
            BackendMode.LOCAL, BackendMode.LOCAL_CON_SINCRONIZACION -> local.registrarEntrada(entrada)
            BackendMode.REMOTO -> remote.registrarEntrada(entrada)
        }
    }
}
