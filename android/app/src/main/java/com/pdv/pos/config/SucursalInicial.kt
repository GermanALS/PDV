package com.pdv.pos.config

import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.repository.SucursalRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

// Preseleccion de la sucursal por defecto al arrancar en LOCAL (PLAN.md Parte
// 3, "Sucursal por defecto"; hallazgo de la verificacion del release, Parte
// 40): sin ella una instalacion limpia no tiene sucursal guardada y venta,
// caja y chat la exigen. En los modos con backend la elige
// ConfiguracionViewModel contra el catalogo remoto.
@Singleton
class SucursalInicial @Inject constructor(
    private val preferences: ConfiguracionPreferences,
    private val sucursalRepository: SucursalRepository,
) {
    /** Persists the first local sucursal when the device is in LOCAL mode and none is saved yet. */
    suspend fun preseleccionarSiFalta() {
        val config = preferences.deviceConfig.first()
        if (config.backendMode != BackendMode.LOCAL || config.sucursalIdSeleccionada != null) return
        val primera = sucursalRepository.observeSucursales().first().first()
        preferences.setSucursalSeleccionada(primera.id)
    }
}
