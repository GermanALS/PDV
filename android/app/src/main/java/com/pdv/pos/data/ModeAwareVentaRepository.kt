package com.pdv.pos.data

import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.local.LocalVentaRepository
import com.pdv.pos.data.remote.RemoteVentaRepository
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.Venta
import com.pdv.pos.domain.repository.VentaRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

// Resuelve local/remota segun BackendMode en DataStore, mismo criterio que
// ModeAwareSucursalRepository (PLAN.md Parte 6, sub-paso 5): "local con
// sincronizacion" escribe en Room igual que "local" - el disparo del sync
// en background hacia el remoto es infraestructura aparte.
@Singleton
class ModeAwareVentaRepository @Inject constructor(
    private val local: LocalVentaRepository,
    private val remote: RemoteVentaRepository,
    private val preferences: ConfiguracionPreferences,
) : VentaRepository {

    override suspend fun registrarVenta(venta: Venta) {
        when (preferences.deviceConfig.first().backendMode) {
            BackendMode.LOCAL, BackendMode.LOCAL_CON_SINCRONIZACION -> local.registrarVenta(venta)
            BackendMode.REMOTO -> remote.registrarVenta(venta)
        }
    }
}
