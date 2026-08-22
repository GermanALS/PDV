package com.pdv.pos.rol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.domain.model.Rol
import com.pdv.pos.domain.repository.RolRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RolViewModel @Inject constructor(
    private val rolRepository: RolRepository,
    private val preferences: ConfiguracionPreferences,
    private val sessionManager: SessionManager,
) : ViewModel() {

    // Mismo motivo que UsuarioViewModel/InventarioViewModel: fuerza refetch
    // en modo REMOTO tras cada escritura.
    private val version = MutableStateFlow(0)

    private val _uiState = MutableStateFlow(RolUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            version.flatMapLatest { rolRepository.observeRoles() }
                .collect { roles -> _uiState.update { it.copy(roles = roles) } }
        }
    }

    fun onNombreChange(valor: String) {
        _uiState.update { it.copy(nombre = valor, error = null) }
    }

    fun onModuloToggle(modulo: String) {
        _uiState.update {
            val actualizados = if (modulo in it.modulosSeleccionados) {
                it.modulosSeleccionados - modulo
            } else {
                it.modulosSeleccionados + modulo
            }
            it.copy(modulosSeleccionados = actualizados)
        }
    }

    fun onEditarClick(rol: Rol) {
        _uiState.update {
            it.copy(
                rolEnEdicionId = rol.id,
                esSistemaEnEdicion = rol.esSistema,
                nombre = rol.nombre,
                modulosSeleccionados = rol.modulosPermitidos.toSet(),
                error = null,
            )
        }
    }

    fun onCancelarEdicionClick() {
        _uiState.update { limpiarFormulario(it) }
    }

    fun onGuardarClick() {
        val estado = _uiState.value
        val nombre = estado.nombre.trim()
        if (nombre.isEmpty() || estado.modulosSeleccionados.isEmpty()) {
            _uiState.update { it.copy(error = "Completa el nombre y selecciona al menos un módulo") }
            return
        }
        val duplicado = estado.roles.any {
            it.nombre.equals(nombre, ignoreCase = true) && it.id != estado.rolEnEdicionId
        }
        if (duplicado) {
            _uiState.update { it.copy(error = "Ya existe un rol con ese nombre") }
            return
        }

        val rol = Rol(
            id = estado.rolEnEdicionId ?: UUID.randomUUID().toString(),
            nombre = nombre,
            modulosPermitidos = estado.modulosSeleccionados.toList(),
        )

        viewModelScope.launch {
            val credenciales = obtenerSucursalYActor()
            if (credenciales == null) {
                _uiState.update { it.copy(error = "No se pudo guardar: falta sucursal o sesión activa") }
                return@launch
            }
            val (sucursalId, actor) = credenciales
            try {
                if (estado.editando) {
                    rolRepository.actualizarRol(rol, sucursalId, actor)
                } else {
                    rolRepository.crearRol(rol, sucursalId, actor)
                }
            } catch (e: IOException) {
                _uiState.update { it.copy(mensajeConfirmacion = "No se pudo guardar el rol: ${e.message}") }
                return@launch
            } catch (e: HttpException) {
                _uiState.update { it.copy(mensajeConfirmacion = "No se pudo guardar el rol: ${e.message}") }
                return@launch
            }
            version.update { it + 1 }
            _uiState.update {
                limpiarFormulario(it).copy(
                    mensajeConfirmacion = if (estado.editando) "Rol actualizado" else "Rol creado",
                )
            }
        }
    }

    fun onEliminarClick(rolId: String) {
        viewModelScope.launch {
            val credenciales = obtenerSucursalYActor()
            if (credenciales == null) {
                _uiState.update { it.copy(mensajeConfirmacion = "No se pudo eliminar: falta sucursal o sesión activa") }
                return@launch
            }
            val (sucursalId, actor) = credenciales
            try {
                rolRepository.eliminarRol(rolId, sucursalId, actor)
            } catch (e: IOException) {
                _uiState.update { it.copy(mensajeConfirmacion = "No se pudo eliminar el rol: ${e.message}") }
                return@launch
            } catch (e: HttpException) {
                _uiState.update { it.copy(mensajeConfirmacion = "No se pudo eliminar el rol: ${e.message}") }
                return@launch
            }
            version.update { it + 1 }
            _uiState.update { limpiarFormulario(it).copy(mensajeConfirmacion = "Rol eliminado") }
        }
    }

    fun descartarMensajeConfirmacion() {
        _uiState.update { it.copy(mensajeConfirmacion = null) }
    }

    private suspend fun obtenerSucursalYActor(): Pair<String, String>? {
        val sucursalId = preferences.deviceConfig.first().sucursalIdSeleccionada
        val actor = sessionManager.session.value?.username
        return if (sucursalId != null && actor != null) sucursalId to actor else null
    }

    private fun limpiarFormulario(estado: RolUiState) = estado.copy(
        rolEnEdicionId = null,
        esSistemaEnEdicion = false,
        nombre = "",
        modulosSeleccionados = emptySet(),
        error = null,
    )
}
