package com.pdv.pos.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.Sucursal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguracionScreen(
    onBack: () -> Unit,
    viewModel: ConfiguracionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración") },
                navigationIcon = { Button(onClick = onBack) { Text("Atrás") } },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ConexionSection(
                ip = uiState.ip,
                puerto = uiState.puerto,
                nombreBaseDatos = uiState.nombreBaseDatos,
                onIpChange = viewModel::onIpChange,
                onPuertoChange = viewModel::onPuertoChange,
                onNombreBaseDatosChange = viewModel::onNombreBaseDatosChange,
                onGuardarConexion = viewModel::onGuardarConexion,
            )
            SucursalSection(
                sucursales = uiState.sucursales,
                seleccionada = uiState.sucursalSeleccionada,
                onSucursalSelected = viewModel::onSucursalSelected,
            )
            ModoSection(modo = uiState.modo, onModoSelected = viewModel::onModoSelected)
            PermisosSection(permisos = uiState.permisosSimulados)
            Button(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth()) {
                Text("Cerrar sesión")
            }
        }
    }
}

@Composable
private fun ConexionSection(
    ip: String,
    puerto: String,
    nombreBaseDatos: String,
    onIpChange: (String) -> Unit,
    onPuertoChange: (String) -> Unit,
    onNombreBaseDatosChange: (String) -> Unit,
    onGuardarConexion: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Conexión al backend", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = ip,
            onValueChange = onIpChange,
            label = { Text("IP") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = puerto,
            onValueChange = onPuertoChange,
            label = { Text("Puerto") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = nombreBaseDatos,
            onValueChange = onNombreBaseDatosChange,
            label = { Text("Nombre de base de datos") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onGuardarConexion, modifier = Modifier.fillMaxWidth()) {
            Text("Guardar conexión")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SucursalSection(
    sucursales: List<Sucursal>,
    seleccionada: Sucursal?,
    onSucursalSelected: (Sucursal) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Sucursal", style = MaterialTheme.typography.titleMedium)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = seleccionada?.nombre.orEmpty(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Sucursal seleccionada") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                sucursales.forEach { sucursal ->
                    DropdownMenuItem(
                        text = { Text(sucursal.nombre) },
                        onClick = {
                            onSucursalSelected(sucursal)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModoSection(modo: BackendMode, onModoSelected: (BackendMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Modo", style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            BackendMode.entries.forEachIndexed { index, opcion ->
                SegmentedButton(
                    selected = modo == opcion,
                    onClick = { onModoSelected(opcion) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = BackendMode.entries.size),
                ) {
                    Text(opcion.etiqueta())
                }
            }
        }
    }
}

private fun BackendMode.etiqueta(): String = when (this) {
    BackendMode.LOCAL -> "Local"
    BackendMode.REMOTO -> "Remoto"
    BackendMode.LOCAL_CON_SINCRONIZACION -> "Local con sincronización"
}

@Composable
private fun PermisosSection(permisos: List<PermisoModulo>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Permisos (simulados)", style = MaterialTheme.typography.titleMedium)
        permisos.forEach { permiso ->
            val estado = if (permiso.habilitado) "habilitado" else "deshabilitado"
            Text("${permiso.nombreModulo}: $estado", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
