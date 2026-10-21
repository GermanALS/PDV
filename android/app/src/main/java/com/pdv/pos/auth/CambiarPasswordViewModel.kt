package com.pdv.pos.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdv.pos.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

// Cambio de contrasena obligatorio de una sesion con debeCambiarPassword
// (PLAN.md Parte 37, A-3): MainActivity muestra esta pantalla en lugar de la
// navegacion hasta que el cambio termina y la sesion apaga la marca.
@HiltViewModel
class CambiarPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CambiarPasswordUiState())
    val uiState: StateFlow<CambiarPasswordUiState> = _uiState.asStateFlow()

    fun onNuevaPasswordChange(value: String) {
        _uiState.update { it.copy(nuevaPassword = value, errorMessage = null) }
    }

    fun onConfirmacionChange(value: String) {
        _uiState.update { it.copy(confirmacion = value, errorMessage = null) }
    }

    fun guardar() {
        val state = _uiState.value
        val session = sessionManager.session.value ?: return
        val error = validar(state)
        if (error != null) {
            _uiState.update { it.copy(errorMessage = error) }
            return
        }
        _uiState.update { it.copy(isGuardando = true) }
        viewModelScope.launch {
            try {
                authRepository.cambiarPassword(session.usuarioId, state.nuevaPassword)
            } catch (e: IOException) {
                _uiState.update { it.copy(isGuardando = false, errorMessage = "No se pudo conectar con el servidor.") }
                return@launch
            } catch (e: HttpException) {
                _uiState.update { it.copy(isGuardando = false, errorMessage = "El servidor respondió con un error (${e.code()}).") }
                return@launch
            }
            sessionManager.iniciarSesion(session.copy(debeCambiarPassword = false))
        }
    }

    fun cerrarSesion() {
        sessionManager.logout()
    }

    private fun validar(state: CambiarPasswordUiState): String? = when {
        state.nuevaPassword.isBlank() -> "Escribe la nueva contraseña"
        state.nuevaPassword != state.confirmacion -> "Las contraseñas no coinciden"
        state.nuevaPassword == ADMIN_BOOTSTRAP_PASSWORD -> "La nueva contraseña no puede ser la inicial"
        else -> null
    }
}
