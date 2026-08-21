package com.pdv.pos.usuario

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pdv.pos.domain.model.Usuario

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsuarioScreen(
    onBack: () -> Unit,
    viewModel: UsuarioViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Administración de usuarios") },
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
            FormularioUsuario(
                uiState = uiState,
                onUsernameChange = viewModel::onUsernameChange,
                onNombreCompletoChange = viewModel::onNombreCompletoChange,
                onRolSelected = viewModel::onRolSelected,
                onActivoChange = viewModel::onActivoChange,
                onGuardar = viewModel::onGuardarClick,
                onCancelar = viewModel::onCancelarEdicionClick,
            )
            uiState.mensajeConfirmacion?.let { mensaje ->
                Text(mensaje, style = MaterialTheme.typography.bodyMedium)
            }
            ListaUsuarios(
                usuarios = uiState.usuarios,
                onEditar = viewModel::onEditarClick,
                onEliminar = viewModel::onEliminarClick,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormularioUsuario(
    uiState: UsuarioUiState,
    onUsernameChange: (String) -> Unit,
    onNombreCompletoChange: (String) -> Unit,
    onRolSelected: (RolUsuario) -> Unit,
    onActivoChange: (Boolean) -> Unit,
    onGuardar: () -> Unit,
    onCancelar: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (uiState.editando) "Editar usuario" else "Nuevo usuario",
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = uiState.username,
                onValueChange = onUsernameChange,
                label = { Text("Usuario") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.nombreCompleto,
                onValueChange = onNombreCompletoChange,
                label = { Text("Nombre completo") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Rol", style = MaterialTheme.typography.bodyMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                RolUsuario.entries.forEachIndexed { index, opcion ->
                    SegmentedButton(
                        selected = uiState.rolSeleccionado == opcion,
                        onClick = { onRolSelected(opcion) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = RolUsuario.entries.size),
                    ) {
                        Text(opcion.etiqueta())
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Activo", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = uiState.activo, onCheckedChange = onActivoChange)
            }
            uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onGuardar, modifier = Modifier.weight(1f)) {
                    Text(if (uiState.editando) "Guardar cambios" else "Crear usuario")
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
private fun ListaUsuarios(
    usuarios: List<Usuario>,
    onEditar: (Usuario) -> Unit,
    onEliminar: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Usuarios", style = MaterialTheme.typography.titleMedium)
        if (usuarios.isEmpty()) {
            Text("Sin usuarios registrados", style = MaterialTheme.typography.bodyMedium)
        }
        usuarios.forEach { usuario ->
            UsuarioRow(usuario = usuario, onEditar = onEditar, onEliminar = onEliminar)
        }
    }
}

@Composable
private fun UsuarioRow(
    usuario: Usuario,
    onEditar: (Usuario) -> Unit,
    onEliminar: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Text(usuario.nombreCompleto, style = MaterialTheme.typography.bodyLarge)
            Text(
                "@${usuario.username} · ${usuario.rol.aRolUsuario().etiqueta()}${if (!usuario.activo) " · inactivo" else ""}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedButton(onClick = { onEditar(usuario) }) { Text("Editar") }
        OutlinedButton(onClick = { onEliminar(usuario.id) }) { Text("Eliminar") }
    }
}
