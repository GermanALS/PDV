package com.pdv.pos.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.remote.ApiResult
import com.pdv.pos.data.remote.HealthApiService
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.repository.RolRepository
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HelloViewModel @Inject constructor(
    private val healthApiService: HealthApiService,
    private val sessionManager: SessionManager,
    private val rolRepository: RolRepository,
    private val preferences: ConfiguracionPreferences,
    private val appLogger: AppLogger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HelloUiState(username = sessionManager.session.value?.username.orEmpty())
    )
    val uiState: StateFlow<HelloUiState> = _uiState.asStateFlow()

    init {
        observarModoBackend()
        observarModulosPermitidos()
    }

    // En LOCAL no se consulta el backend (CLAUDE.md seccion 3: el modo local
    // funciona sin red); al cambiar de modo desde Configuracion se reevalua.
    private fun observarModoBackend() {
        viewModelScope.launch {
            preferences.deviceConfig
                .map { it.backendMode }
                .distinctUntilChanged()
                .collectLatest { modo ->
                    _uiState.update { it.copy(backendMode = modo, healthResult = null) }
                    if (modo != BackendMode.LOCAL) {
                        val result = consultarHealth()
                        _uiState.update { it.copy(healthResult = result) }
                    }
                }
        }
    }

    private suspend fun consultarHealth(): ApiResult<String> = try {
        val response = healthApiService.getHealth()
        ApiResult.Success("${response.status} (v${response.version})")
    } catch (e: IOException) {
        ApiResult.Error(e.message ?: "No se pudo conectar al backend")
    }

    // Activacion/desactivacion de modulos segun el rol de la sesion activa
    // (PLAN.md Parte 13, checklist "Roles y permisos" - wiring diferido del
    // sub-paso 1 al 2, ahora que la sesion trae el rolId real).
    private fun observarModulosPermitidos() {
        viewModelScope.launch {
            sessionManager.session
                .flatMapLatest { session ->
                    if (session == null) {
                        flowOf(emptySet<String>())
                    } else {
                        rolRepository.observeRoles().map { roles ->
                            roles.find { it.id == session.rolId }?.modulosPermitidos?.toSet() ?: emptySet()
                        }
                    }
                }
                .collect { modulos -> _uiState.update { it.copy(modulosPermitidos = modulos) } }
        }
    }

    // Segunda compuerta ademas de ocultar el boton (PLAN.md Parte 13,
    // checklist "Logging"): un intento de navegar a un modulo sin permiso
    // queda registrado con categoria AUTH, independiente de si la UI ya lo
    // habia ocultado - mismo punto de verificacion que usaran las Partes
    // 14-16 antes de que la IA ejecute una accion.
    fun onIntentoNavegar(modulo: String): Boolean {
        val permitido = modulo in _uiState.value.modulosPermitidos
        if (!permitido) {
            viewModelScope.launch {
                // Sin fallback la denegacion queda sin rastro cuando no hay
                // sucursal seleccionada todavia (posible: el login no lo
                // exige) - mismo criterio que LastWriteWinsSyncEngine.
                val sucursalId = preferences.deviceConfig.first().sucursalIdSeleccionada ?: "-"
                val username = sessionManager.session.value?.username
                if (username != null) {
                    appLogger.log(
                        LogType.AUTH,
                        sucursalId = sucursalId,
                        usuario = username,
                        mensaje = "Acceso denegado al modulo: $modulo",
                    )
                }
            }
        }
        return permitido
    }

    fun logout() {
        sessionManager.logout()
    }
}
