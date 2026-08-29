package com.pdv.pos.conflictos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pdv.pos.ui.theme.success

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RevisionConflictosScreen(
    onBack: () -> Unit,
    viewModel: RevisionConflictosViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Revisión de conflictos de sincronización") },
                navigationIcon = { Button(onClick = onBack) { Text("Atrás") } },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FiltroSelector(filtro = uiState.filtro, onFiltroSelected = viewModel::onFiltroSelected)

            val conflictos = uiState.conflictosFiltrados
            if (conflictos.isEmpty()) {
                Text("Sin conflictos registrados", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(conflictos) { conflicto -> ConflictoCard(conflicto) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FiltroSelector(
    filtro: FiltroConflicto,
    onFiltroSelected: (FiltroConflicto) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        FiltroConflicto.entries.forEachIndexed { index, opcion ->
            SegmentedButton(
                selected = filtro == opcion,
                onClick = { onFiltroSelected(opcion) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = FiltroConflicto.entries.size),
            ) {
                Text(opcion.etiqueta)
            }
        }
    }
}

@Composable
private fun ConflictoCard(conflicto: ConflictoUi) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(conflicto.entidad, style = MaterialTheme.typography.titleMedium)
                EstadoChip(resueltoAutomaticamente = conflicto.resueltoAutomaticamente)
            }
            Text(
                "${conflicto.fechaDeteccion} · ${conflicto.politicaAplicada}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "local_id: ${conflicto.entidadLocalId} · sucursal: ${conflicto.sucursalId ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
            )
            ValorLinea(etiqueta = "Local", valor = conflicto.valorLocal)
            ValorLinea(etiqueta = "Remoto", valor = conflicto.valorRemoto)
            ValorLinea(etiqueta = "Resuelto", valor = conflicto.valorResuelto)
        }
    }
}

@Composable
private fun EstadoChip(resueltoAutomaticamente: Boolean) {
    val texto = if (resueltoAutomaticamente) "Auto-resuelto" else "Pendiente de revisión"
    val color = if (resueltoAutomaticamente) MaterialTheme.success else MaterialTheme.colorScheme.error
    Text(texto, style = MaterialTheme.typography.labelMedium, color = color)
}

@Composable
private fun ValorLinea(etiqueta: String, valor: String) {
    Text("$etiqueta: $valor", style = MaterialTheme.typography.bodySmall)
}

private val FiltroConflicto.etiqueta: String
    get() = when (this) {
        FiltroConflicto.TODOS -> "Todos"
        FiltroConflicto.PENDIENTES -> "Pendientes"
        FiltroConflicto.AUTO_RESUELTOS -> "Auto-resueltos"
    }
