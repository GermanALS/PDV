package com.pdv.pos.usuario

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdv.pos.auth.PasswordHasher
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.domain.model.Usuario
import com.pdv.pos.domain.repository.RolRepository
import com.pdv.pos.domain.repository.UsuarioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    private val rolRepository: RolRepository,
    private val preferences: ConfiguracionPreferences,
    private val sessionManager: SessionManager,
    private val passwordHasher: PasswordHasher,
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
            combine(
                version.flatMapLatest { usuarioRepository.observeUsuarios() },
                rolRepository.observeRoles(),
            ) { usuarios, roles -> usuarios to roles }
                .collect { (usuarios, roles) ->
                    _uiState.update {
                        it.copy(
                            usuarios = usuarios,
                            roles = roles,
                            rolIdSeleccionado = it.rolIdSeleccionado ?: roles.firstOrNull()?.id,
                        )
                    }
                }
        }
    }

    fun onUsernameChange(valor: String) {
        _uiState.update { it.copy(username = valor, error = null) }
    }

    fun onNombreCompletoChange(valor: String) {
        _uiState.update { it.copy(nombreCompleto = valor, error = null) }
    }

    fun onRolSelected(rolId: String) {
        _uiState.update { it.copy(rolIdSeleccionado = rolId) }
    }

    fun onActivoChange(valor: Boolean) {
        _uiState.update { it.copy(activo = valor) }
    }

    fun onPasswordChange(valor: String) {
        _uiState.update { it.copy(password = valor, error = null) }
    }

    fun onEditarClick(usuario: Usuario) {
        _uiState.update {
            it.copy(
                usuarioEnEdicionId = usuario.id,
                username = usuario.username,
                nombreCompleto = usuario.nombreCompleto,
                rolIdSeleccionado = usuario.rolId,
                activo = usuario.activo,
                password = "",
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
        val rolId = estado.rolIdSeleccionado
        if (username.isEmpty() || nombreCompleto.isEmpty() || rolId == null) {
            _uiState.update { it.copy(error = "Completa usuario, nombre completo y rol") }
            return
        }
        // Solo al crear: al editar, vacio significa "no cambiar la
        // contrasena existente" (observacion del usuario tras la
        // verificacion en dispositivo, PLAN.md Parte 13 sub-paso 4).
        if (!estado.editando && estado.password.isBlank()) {
            _uiState.update { it.copy(error = "La contraseña es obligatoria al crear un usuario") }
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
            rolId = rolId,
            activo = estado.activo,
        )
        viewModelScope.launch {
            val credenciales = obtenerSucursalYActor()
            if (credenciales == null) {
                _uiState.update { it.copy(error = "No se pudo guardar: falta sucursal o sesión activa") }
                return@launch
            }
            val (sucursalId, actor) = credenciales
            // Vacio = no cambiar la contrasena existente al editar (semantica
            // PATCH); al crear, vacio deja al usuario sin contrasena asignada.
            val passwordHash = estado.password.takeIf { it.isNotBlank() }?.let { passwordHasher.hash(it) }
            try {
                if (estado.editando) {
                    usuarioRepository.actualizarUsuario(usuario, passwordHash, sucursalId, actor)
                } else {
                    usuarioRepository.crearUsuario(usuario, passwordHash, sucursalId, actor)
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
        rolIdSeleccionado = estado.roles.firstOrNull()?.id,
        activo = true,
        password = "",
        error = null,
    )
}
