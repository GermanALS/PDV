package com.pdv.pos.acercade

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class AcercaDeUiState(
    val version: String,
    val fecha: String,
    val novedades: List<String>,
    val limitaciones: List<String>,
    val equipo: String,
    val contacto: String,
)

@HiltViewModel
class AcercaDeViewModel @Inject constructor(
    infoRelease: InfoRelease,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AcercaDeUiState(
            version = "${infoRelease.versionName} (versionCode ${infoRelease.versionCode})",
            fecha = infoRelease.fecha,
            novedades = infoRelease.novedades,
            limitaciones = infoRelease.limitaciones,
            equipo = infoRelease.equipo,
            contacto = infoRelease.contacto,
        )
    )
    val uiState: StateFlow<AcercaDeUiState> = _uiState.asStateFlow()
}
