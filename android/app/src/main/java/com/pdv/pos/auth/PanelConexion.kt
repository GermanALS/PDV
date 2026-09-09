package com.pdv.pos.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pdv.pos.config.etiqueta
import com.pdv.pos.data.remote.ApiResult
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.EsquemaConexion

// Panel de conexion invocable desde el login (PLAN.md Parte 31). Se muestra
// como hoja inferior modal: el login se renderiza fuera del NavHost cuando no
// hay sesion (MainActivity), asi que no puede usar navegacion.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelConexionBottomSheet(
    onDismiss: () -> Unit,
    viewModel: PanelConexionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Conexión al backend", style = MaterialTheme.typography.titleLarge)

            Text("Modo", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                BackendMode.entries.forEachIndexed { index, opcion ->
                    SegmentedButton(
                        selected = uiState.modo == opcion,
                        onClick = { viewModel.onModoSelected(opcion) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = BackendMode.entries.size),
                    ) {
                        Text(opcion.etiqueta())
                    }
                }
            }

            if (uiState.modo == BackendMode.LOCAL) {
                Text(
                    "En modo local la app no se conecta a ningún backend.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text("Servidor remoto", style = MaterialTheme.typography.titleMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    EsquemaConexion.entries.forEachIndexed { index, opcion ->
                        SegmentedButton(
                            selected = uiState.esquema == opcion,
                            onClick = { viewModel.onEsquemaSelected(opcion) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = EsquemaConexion.entries.size),
                        ) {
                            Text(opcion.name)
                        }
                    }
                }
                OutlinedTextField(
                    value = uiState.host,
                    onValueChange = viewModel::onHostChange,
                    label = { Text("IP / Servidor") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uiState.puerto,
                    onValueChange = viewModel::onPuertoChange,
                    label = { Text("Puerto") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::onGuardar) { Text("Guardar") }
                    OutlinedButton(onClick = viewModel::onProbarConexion, enabled = !uiState.probando) {
                        Text(if (uiState.probando) "Probando..." else "Probar conexión")
                    }
                }
                when (val resultado = uiState.resultadoPrueba) {
                    is ApiResult.Success -> Text(
                        "Conexión correcta con el backend.",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    is ApiResult.Error -> Text(
                        resultado.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    null -> Unit
                }
            }
        }
    }
}
