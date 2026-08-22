package com.pdv.pos.rol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pdv.pos.domain.model.Rol

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RolScreen(
    onBack: () -> Unit,
    viewModel: RolViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Roles") },
                navigationIcon = { Button(onClick = onBack) { Text("Atrás") } },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            FormularioRol(
                uiState = uiState,
                onNombreChange = viewModel::onNombreChange,
                onModuloToggle = viewModel::onModuloToggle,
                onGuardar = viewModel::onGuardarClick,
                onCancelar = viewModel::onCancelarEdicionClick,
            )
            uiState.mensajeConfirmacion?.let { mensaje ->
                Text(mensaje, style = MaterialTheme.typography.bodyMedium)
            }
            ListaRoles(
                roles = uiState.roles,
                onEditar = viewModel::onEditarClick,
                onEliminar = viewModel::onEliminarClick,
            )
        }
    }
}

@Composable
private fun FormularioRol(
    uiState: RolUiState,
    onNombreChange: (String) -> Unit,
    onModuloToggle: (String) -> Unit,
    onGuardar: () -> Unit,
    onCancelar: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (uiState.editando) "Editar rol" else "Nuevo rol",
                style = MaterialTheme.typography.titleMedium,
            )
            if (uiState.esSistemaEnEdicion) {
                Text(
                    "Los roles de sistema no se pueden modificar",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedTextField(
                value = uiState.nombre,
                onValueChange = onNombreChange,
                label = { Text("Nombre") },
                enabled = !uiState.esSistemaEnEdicion,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Módulos permitidos", style = MaterialTheme.typography.bodyMedium)
            MODULOS_DISPONIBLES.forEach { modulo ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = modulo in uiState.modulosSeleccionados,
                        onCheckedChange = { onModuloToggle(modulo) },
                        enabled = !uiState.esSistemaEnEdicion,
                    )
                    Text(modulo, style = MaterialTheme.typography.bodyMedium)
                }
            }
            uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!uiState.esSistemaEnEdicion) {
                    Button(onClick = onGuardar, modifier = Modifier.weight(1f)) {
                        Text(if (uiState.editando) "Guardar cambios" else "Crear rol")
                    }
                }
                if (uiState.editando) {
                    OutlinedButton(onClick = onCancelar, modifier = Modifier.weight(1f)) {
                        Text("Cancelar")
                    }
                }
            }
        }
    }
}

@Composable
private fun ListaRoles(
    roles: List<Rol>,
    onEditar: (Rol) -> Unit,
    onEliminar: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Roles existentes", style = MaterialTheme.typography.titleMedium)
        if (roles.isEmpty()) {
            Text("Sin roles registrados", style = MaterialTheme.typography.bodyMedium)
        }
        roles.forEach { rol ->
            RolRow(rol = rol, onEditar = onEditar, onEliminar = onEliminar)
        }
    }
}

@Composable
private fun RolRow(
    rol: Rol,
    onEditar: (Rol) -> Unit,
    onEliminar: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Text(
                rol.nombre + if (rol.esSistema) " (sistema)" else "",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(rol.modulosPermitidos.joinToString(", "), style = MaterialTheme.typography.bodySmall)
        }
        if (!rol.esSistema) {
            OutlinedButton(onClick = { onEditar(rol) }) { Text("Editar") }
            OutlinedButton(onClick = { onEliminar(rol.id) }) { Text("Eliminar") }
        }
    }
}
