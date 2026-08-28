package com.pdv.pos.inventario.importacion

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// Glue de Android (Uri/ContentResolver) para ImportadorCatalogo - sin logica
// de negocio propia, por eso no tiene test unitario aparte (mismo criterio
// que VoiceInputButton/BarcodeAnalyzer: se verifica needs-device).
@HiltViewModel
class ImportacionCatalogoViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importador: ImportadorCatalogo,
    private val preferences: ConfiguracionPreferences,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportacionCatalogoUiState())
    val uiState: StateFlow<ImportacionCatalogoUiState> = _uiState.asStateFlow()

    fun onArchivoSeleccionado(uri: Uri) {
        if (_uiState.value.procesando) return
        _uiState.value = ImportacionCatalogoUiState(procesando = true)

        viewModelScope.launch {
            val sucursalId = preferences.deviceConfig.first().sucursalIdSeleccionada
            val usuarioId = sessionManager.session.value?.username
            if (sucursalId == null || usuarioId == null) {
                _uiState.value = ImportacionCatalogoUiState(mensaje = "No se pudo importar: falta sucursal o sesión activa")
                return@launch
            }

            val contenido = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }
            if (contenido == null) {
                _uiState.value = ImportacionCatalogoUiState(mensaje = "No se pudo leer el archivo seleccionado")
                return@launch
            }

            val resultado = importador.importar(contenido, sucursalId, usuarioId) { procesados, total ->
                _uiState.value = _uiState.value.copy(procesados = procesados, total = total)
            }
            _uiState.value = when (resultado) {
                ResultadoImportacion.EncabezadoInvalido -> ImportacionCatalogoUiState(encabezadoInvalido = true)
                is ResultadoImportacion.Completado -> ImportacionCatalogoUiState(resumen = resultado.resumen)
            }
        }
    }

    fun onResultadoDescartado() {
        _uiState.value = ImportacionCatalogoUiState()
    }
}
