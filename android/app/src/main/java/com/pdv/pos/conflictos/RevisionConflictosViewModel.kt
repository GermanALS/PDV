package com.pdv.pos.conflictos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdv.pos.domain.model.SyncConflict
import com.pdv.pos.domain.repository.SyncConflictRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Auditoría de solo lectura (PLAN.md Parte 19). El repositorio resuelve
 * local/remoto según `BackendMode` (`ModeAwareSyncConflictRepository`).
 */
@HiltViewModel
class RevisionConflictosViewModel @Inject constructor(
    private val repository: SyncConflictRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RevisionConflictosUiState())
    val uiState: StateFlow<RevisionConflictosUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeConflictos().collect { conflictos ->
                _uiState.update { it.copy(conflictos = conflictos.map(SyncConflict::toUi)) }
            }
        }
    }

    fun onFiltroSelected(filtro: FiltroConflicto) {
        _uiState.update { it.copy(filtro = filtro) }
    }
}

private val formatoFechaHora = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

private fun SyncConflict.toUi() = ConflictoUi(
    entidad = entidad,
    fechaDeteccion = formatoFechaHora.format(Date(fechaDeteccion)),
    politicaAplicada = politicaAplicada,
    entidadLocalId = entidadLocalId,
    sucursalId = sucursalId,
    valorLocal = valorLocal,
    valorRemoto = valorRemoto,
    valorResuelto = valorResuelto,
    resueltoAutomaticamente = resueltoAutomaticamente,
)
