package com.pdv.pos.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.remote.BackendHealthChecker
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.EsquemaConexion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Panel de conexion reducido, accesible desde el login sin sesion (PLAN.md
// Parte 31, decision 2026-09-09: version dedicada, no la pantalla
// Configuracion completa). Comparte ConfiguracionPreferences con el modulo
// Configuracion, asi que un cambio hecho aca se ve alla y viceversa.
@HiltViewModel
class PanelConexionViewModel @Inject constructor(
    private val preferences: ConfiguracionPreferences,
    private val healthChecker: BackendHealthChecker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PanelConexionUiState())
    val uiState: StateFlow<PanelConexionUiState> = _uiState.asStateFlow()

    init {
        // El modo se persiste al instante (onModoSelected) y se mantiene al dia
        // desde DataStore. esquema/host/puerto se siembran una sola vez y de ahi
        // son borrador local hasta onGuardar(), para no pisar una edicion en
        // curso (mismo criterio que ConfiguracionViewModel).
        viewModelScope.launch {
            val inicial = preferences.deviceConfig.first()
            _uiState.update {
                it.copy(
                    modo = inicial.backendMode,
                    esquema = inicial.esquema,
                    host = inicial.ip,
                    puerto = inicial.puerto,
                )
            }
        }
        viewModelScope.launch {
            preferences.deviceConfig.collect { config ->
                _uiState.update { it.copy(modo = config.backendMode) }
            }
        }
    }

    fun onModoSelected(modo: BackendMode) {
        viewModelScope.launch { preferences.setBackendMode(modo) }
    }

    fun onEsquemaSelected(esquema: EsquemaConexion) {
        _uiState.update { it.copy(esquema = esquema) }
    }

    fun onHostChange(value: String) {
        _uiState.update { it.copy(host = value) }
    }

    fun onPuertoChange(value: String) {
        _uiState.update { it.copy(puerto = value) }
    }

    fun onGuardar() {
        val estado = _uiState.value
        viewModelScope.launch {
            preferences.setConexion(estado.esquema, estado.host.trim(), estado.puerto.trim())
        }
    }

    fun onProbarConexion() {
        val estado = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(probando = true, resultadoPrueba = null) }
            val resultado = healthChecker.probar(estado.esquema, estado.host, estado.puerto)
            _uiState.update { it.copy(probando = false, resultadoPrueba = resultado) }
        }
    }
}
