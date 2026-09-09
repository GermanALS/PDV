package com.pdv.pos.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdv.pos.domain.repository.AuthRepository
import com.pdv.pos.domain.repository.LoginResultado
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onUsernameChange(value: String) {
        _uiState.update { it.copy(username = value, errorMessage = null, connectivityError = false) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null, connectivityError = false) }
    }

    // Al cerrar el panel de conexion se limpia un error de conectividad
    // previo: es probable que el usuario acabe de corregir la configuracion o
    // de cambiar a modo LOCAL (PLAN.md Parte 31, Grupo 2). Un error de
    // credenciales se mantiene, no lo resuelve tocar la conexion.
    fun onConexionConfigurada() {
        _uiState.update {
            if (it.connectivityError) it.copy(errorMessage = null, connectivityError = false) else it
        }
    }

    fun login() {
        val state = _uiState.value
        viewModelScope.launch {
            val resultado = try {
                authRepository.login(state.username, state.password)
            } catch (e: IOException) {
                _uiState.update {
                    it.copy(
                        errorMessage = "No se pudo conectar con el servidor. Revisa la red o la configuración de conexión.",
                        connectivityError = true,
                    )
                }
                return@launch
            } catch (e: HttpException) {
                _uiState.update {
                    it.copy(
                        errorMessage = "El servidor respondió con un error (${e.code()}). Revisa la configuración de conexión.",
                        connectivityError = true,
                    )
                }
                return@launch
            }
            when (resultado) {
                is LoginResultado.Exitoso -> sessionManager.iniciarSesion(
                    Session(
                        username = resultado.usuario.username,
                        usuarioId = resultado.usuario.id,
                        rolId = resultado.usuario.rolId,
                        accessToken = resultado.accessToken,
                    ),
                )
                LoginResultado.CredencialesInvalidas ->
                    _uiState.update {
                        it.copy(errorMessage = "Usuario o contraseña incorrectos", connectivityError = false)
                    }
            }
        }
    }
}
