package com.pdv.pos.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.remote.HealthApiService
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.repository.RolRepository
import com.pdv.pos.domain.repository.SucursalRepository
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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
    private val sucursalRepository: SucursalRepository,
    private val preferences: ConfiguracionPreferences,
    private val appLogger: AppLogger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HelloUiState(username = sessionManager.session.value?.username.orEmpty())
    )
    val uiState: StateFlow<HelloUiState> = _uiState.asStateFlow()

    init {
        observarModoBackend()
        observarSucursal()
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
                    if (modo == BackendMode.LOCAL) {
                        _uiState.update { it.copy(backendMode = modo, conexion = EstadoConexion.SOLO_LOCAL) }
                    } else {
                        _uiState.update { it.copy(backendMode = modo, conexion = EstadoConexion.COMPROBANDO) }
                        val conexion = consultarConexion()
                        _uiState.update { it.copy(conexion = conexion) }
                    }
                }
        }
    }

    private suspend fun consultarConexion(): EstadoConexion = try {
        healthApiService.getHealth()
        EstadoConexion.CONECTADO
    } catch (e: IOException) {
        EstadoConexion.SIN_CONEXION
    }

    private fun observarSucursal() {
        viewModelScope.launch {
            // In REMOTO the catalog comes from the network: without it the
            // name is unknown and EstadoConexion already says so.
            val sucursales = sucursalRepository.observeSucursales().catch { e ->
                if (e !is IOException) throw e
                emit(emptyList())
            }
            // Local and remote copies are related by remoteId (PLAN.md Parte 36, A-8).
            combine(
                preferences.deviceConfig.map { it.sucursalIdSeleccionada }.distinctUntilChanged(),
                sucursales,
            ) { sucursalId, lista ->
                sucursalId?.let { id -> lista.find { it.id == id || it.remoteId == id }?.nombre }
            }
                .collect { nombre -> _uiState.update { it.copy(sucursalNombre = nombre) } }
        }
    }

    // Activacion/desactivacion de modulos segun el rol de la sesion activa
    // (PLAN.md Parte 13, checklist "Roles y permisos" - wiring diferido del
    // sub-paso 1 al 2, ahora que la sesion trae el rolId real).
    private fun observarModulosPermitidos() {
        viewModelScope.launch {
            sessionManager.session
                .flatMapLatest { session ->
                    if (session == null) {
                        flowOf(null)
                    } else {
                        rolRepository.observeRoles().map { roles -> roles.find { it.id == session.rolId } }
                    }
                }
                .collect { rol ->
                    _uiState.update {
                        it.copy(rolNombre = rol?.nombre, modulosPermitidos = rol?.modulosPermitidos?.toSet() ?: emptySet())
                    }
                }
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
