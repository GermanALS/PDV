package com.pdv.pos.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.Sucursal
import com.pdv.pos.domain.repository.SucursalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Permisos por rol simulados hasta la Parte 13 (gestion de usuarios real).
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
    private val preferences: ConfiguracionPreferences,
    private val sucursalRepository: SucursalRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConfiguracionUiState(permisosSimulados = permisosSimuladosDeEjemplo))
    val uiState: StateFlow<ConfiguracionUiState> = _uiState.asStateFlow()

    init {
        // Los campos de conexion (ip/puerto/nombreBaseDatos) se siembran una
        // sola vez desde el valor persistido y de ahi en mas son un borrador
        // puramente local hasta onGuardarConexion(): son preferencia de
        // dispositivo de un solo escritor (este ViewModel), asi que no hace
        // falta mantenerlos sincronizados con cada emision de deviceConfig.
        // Re-derivarlos en cada emision (como sucursal/modo) pisaria una
        // edicion en curso del usuario cada vez que cambia la sucursal o el
        // modo (hallazgo de code-reviewer).
        viewModelScope.launch {
            val inicial = preferences.deviceConfig.first()
            _uiState.update { it.copy(ip = inicial.ip, puerto = inicial.puerto, nombreBaseDatos = inicial.nombreBaseDatos) }
        }
        viewModelScope.launch {
            combine(preferences.deviceConfig, sucursalRepository.observeSucursales()) { config, sucursales ->
                config to sucursales
            }.collect { (config, sucursales) ->
                _uiState.update {
                    it.copy(
                        sucursales = sucursales,
                        sucursalSeleccionada = sucursales.find { sucursal -> sucursal.id == config.sucursalIdSeleccionada }
                            ?: sucursales.firstOrNull(),
                        modo = config.backendMode,
                    )
                }
            }
        }
    }

    fun onIpChange(value: String) {
        _uiState.update { it.copy(ip = value) }
    }

    fun onPuertoChange(value: String) {
        _uiState.update { it.copy(puerto = value) }
    }

    fun onNombreBaseDatosChange(value: String) {
        _uiState.update { it.copy(nombreBaseDatos = value) }
    }

    fun onGuardarConexion() {
        val estado = _uiState.value
        viewModelScope.launch {
            preferences.setConexion(estado.ip, estado.puerto, estado.nombreBaseDatos)
        }
    }

    fun onSucursalSelected(sucursal: Sucursal) {
        viewModelScope.launch {
            preferences.setSucursalSeleccionada(sucursal.id)
        }
    }

    fun onModoSelected(modo: BackendMode) {
        viewModelScope.launch {
            preferences.setBackendMode(modo)
        }
    }

    fun logout() {
        sessionManager.logout()
    }
}
