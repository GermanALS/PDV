package com.pdv.pos.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.data.remote.ApiResult
import com.pdv.pos.data.remote.HealthApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

private fun buildLocalGreeting(): String = "Hola desde una funcion local de Kotlin"

@HiltViewModel
class HelloViewModel @Inject constructor(
    private val healthApiService: HealthApiService,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HelloUiState(
            username = sessionManager.session.value?.username.orEmpty(),
            localGreeting = buildLocalGreeting(),
        )
    )
    val uiState: StateFlow<HelloUiState> = _uiState.asStateFlow()

    init {
        fetchHealth()
    }

    fun fetchHealth() {
        viewModelScope.launch {
            val result = try {
                val response = healthApiService.getHealth()
                ApiResult.Success("${response.status} (v${response.version})")
            } catch (e: IOException) {
                ApiResult.Error(e.message ?: "No se pudo conectar al backend")
            }
            _uiState.value = _uiState.value.copy(healthResult = result)
        }
    }

    fun logout() {
        sessionManager.logout()
    }
}
