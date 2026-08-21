package com.pdv.pos.usuario

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.domain.model.Usuario
import com.pdv.pos.domain.repository.UsuarioRepository
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
class UsuarioViewModel @Inject constructor(
    private val usuarioRepository: UsuarioRepository,
    private val preferences: ConfiguracionPreferences,
    private val sessionManager: SessionManager,
) : ViewModel() {

    // Se incrementa tras cada escritura exitosa para forzar un refetch en
    // modo REMOTO, donde observeUsuarios no es reactivo de verdad (mismo
    // criterio que InventarioViewModel, PLAN.md Parte 9 sub-paso 4) - en
    // modo local el Flow de Room ya se refresca solo.
    private val version = MutableStateFlow(0)

    private val _uiState = MutableStateFlow(UsuarioUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            version.flatMapLatest { usuarioRepository.observeUsuarios() }
                .collect { usuarios -> _uiState.update { it.copy(usuarios = usuarios) } }
        }
    }

    fun onUsernameChange(valor: String) {
        _uiState.update { it.copy(username = valor, error = null) }
    }

    fun onNombreCompletoChange(valor: String) {
        _uiState.update { it.copy(nombreCompleto = valor, error = null) }
    }

    fun onRolSelected(rol: RolUsuario) {
        _uiState.update { it.copy(rolSeleccionado = rol) }
    }

    fun onActivoChange(valor: Boolean) {
        _uiState.update { it.copy(activo = valor) }
    }

    fun onEditarClick(usuario: Usuario) {
        _uiState.update {
            it.copy(
                usuarioEnEdicionId = usuario.id,
                username = usuario.username,
                nombreCompleto = usuario.nombreCompleto,
                rolSeleccionado = usuario.rol.aRolUsuario(),
                activo = usuario.activo,
                error = null,
            )
        }
    }

    fun onCancelarEdicionClick() {
        _uiState.update { limpiarFormulario(it) }
    }

    fun onGuardarClick() {
        val estado = _uiState.value
        val username = estado.username.trim()
        val nombreCompleto = estado.nombreCompleto.trim()
        if (username.isEmpty() || nombreCompleto.isEmpty()) {
            _uiState.update { it.copy(error = "Completa usuario y nombre completo") }
            return
        }
        val duplicado = estado.usuarios.any {
            it.username.equals(username, ignoreCase = true) && it.id != estado.usuarioEnEdicionId
        }
        if (duplicado) {
            _uiState.update { it.copy(error = "Ya existe un usuario con ese nombre de usuario") }
            return
        }

        val usuario = Usuario(
            id = estado.usuarioEnEdicionId ?: UUID.randomUUID().toString(),
            username = username,
            nombreCompleto = nombreCompleto,
            rol = estado.rolSeleccionado.aTextoDominio(),
            activo = estado.activo,
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
                    usuarioRepository.actualizarUsuario(usuario, sucursalId, actor)
                } else {
                    usuarioRepository.crearUsuario(usuario, sucursalId, actor)
                }
            } catch (e: IOException) {
                _uiState.update { it.copy(mensajeConfirmacion = "No se pudo guardar el usuario: ${e.message}") }
                return@launch
            } catch (e: HttpException) {
                _uiState.update { it.copy(mensajeConfirmacion = "No se pudo guardar el usuario: ${e.message}") }
                return@launch
            }
            version.update { it + 1 }
            _uiState.update {
                limpiarFormulario(it).copy(
                    mensajeConfirmacion = if (estado.editando) "Usuario actualizado" else "Usuario creado",
                )
            }
        }
    }

    fun onEliminarClick(usuarioId: String) {
        viewModelScope.launch {
            val credenciales = obtenerSucursalYActor()
            if (credenciales == null) {
                _uiState.update { it.copy(mensajeConfirmacion = "No se pudo eliminar: falta sucursal o sesión activa") }
                return@launch
            }
            val (sucursalId, actor) = credenciales
            try {
                usuarioRepository.eliminarUsuario(usuarioId, sucursalId, actor)
            } catch (e: IOException) {
                _uiState.update { it.copy(mensajeConfirmacion = "No se pudo eliminar el usuario: ${e.message}") }
                return@launch
            } catch (e: HttpException) {
                _uiState.update { it.copy(mensajeConfirmacion = "No se pudo eliminar el usuario: ${e.message}") }
                return@launch
            }
            version.update { it + 1 }
            _uiState.update { limpiarFormulario(it).copy(mensajeConfirmacion = "Usuario eliminado") }
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

    private fun limpiarFormulario(estado: UsuarioUiState) = estado.copy(
        usuarioEnEdicionId = null,
        username = "",
        nombreCompleto = "",
        rolSeleccionado = RolUsuario.ENCARGADO_TURNO,
        activo = true,
        error = null,
    )
}
