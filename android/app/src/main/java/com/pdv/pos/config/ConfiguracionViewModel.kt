package com.pdv.pos.config

import androidx.lifecycle.ViewModel
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.Sucursal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

// Datos de ejemplo del sub-paso 1 (UI); reemplazados por SucursalRepository
// en el sub-paso 2 (Persistencia local, PLAN.md Parte 6).
private val sucursalDeEjemplo = Sucursal(id = "local-default", nombre = "Sucursal principal")

private val permisosSimuladosDeEjemplo = listOf(
    PermisoModulo("Venta de mostrador", habilitado = true),
    PermisoModulo("Entrada de mercancia", habilitado = true),
    PermisoModulo("Inventario", habilitado = true),
    PermisoModulo("Caja", habilitado = true),
    PermisoModulo("Devoluciones", habilitado = true),
    PermisoModulo("Administracion de usuarios", habilitado = true),
)

@HiltViewModel
class ConfiguracionViewModel @Inject constructor(
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ConfiguracionUiState(
            sucursales = listOf(sucursalDeEjemplo),
            sucursalSeleccionada = sucursalDeEjemplo,
            permisosSimulados = permisosSimuladosDeEjemplo,
        )
    )
    val uiState: StateFlow<ConfiguracionUiState> = _uiState.asStateFlow()

    fun onIpChange(value: String) {
        _uiState.value = _uiState.value.copy(ip = value)
    }

    fun onPuertoChange(value: String) {
        _uiState.value = _uiState.value.copy(puerto = value)
    }

    fun onNombreBaseDatosChange(value: String) {
        _uiState.value = _uiState.value.copy(nombreBaseDatos = value)
    }

    fun onSucursalSelected(sucursal: Sucursal) {
        _uiState.value = _uiState.value.copy(sucursalSeleccionada = sucursal)
    }

    fun onModoSelected(modo: BackendMode) {
        _uiState.value = _uiState.value.copy(modo = modo)
    }

    fun logout() {
        sessionManager.logout()
    }
}
