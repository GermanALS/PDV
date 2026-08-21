package com.pdv.pos.data

import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.local.LocalDevolucionRepository
import com.pdv.pos.data.remote.RemoteDevolucionRepository
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.Devolucion
import com.pdv.pos.domain.repository.DevolucionRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

// Resuelve local/remota segun BackendMode en DataStore, mismo criterio que
// ModeAwareVentaRepository (PLAN.md Parte 7, sub-paso 4): "local con
// sincronizacion" escribe en Room igual que "local".
@Singleton
class ModeAwareDevolucionRepository @Inject constructor(
    private val local: LocalDevolucionRepository,
    private val remote: RemoteDevolucionRepository,
    private val preferences: ConfiguracionPreferences,
) : DevolucionRepository {

    override suspend fun registrarDevolucion(devolucion: Devolucion) {
        when (preferences.deviceConfig.first().backendMode) {
            BackendMode.LOCAL, BackendMode.LOCAL_CON_SINCRONIZACION -> local.registrarDevolucion(devolucion)
            BackendMode.REMOTO -> remote.registrarDevolucion(devolucion)
        }
    }
}
