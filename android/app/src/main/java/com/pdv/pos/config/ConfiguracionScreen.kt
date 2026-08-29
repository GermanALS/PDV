package com.pdv.pos.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pdv.pos.data.remote.ApiResult
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.Sucursal
import com.pdv.pos.ia.LlmProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguracionScreen(
    onBack: () -> Unit,
    onNavigateToImportarCatalogo: () -> Unit,
    onNavigateToConflictos: () -> Unit,
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
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ModoSection(modo = uiState.modo, onModoSelected = viewModel::onModoSelected)
            // Conexion y Sucursal solo aplican cuando la app habla con un
            // backend: en LOCAL puro no hay a donde conectarse ni catalogo
            // remoto de sucursales que elegir.
            if (uiState.modo != BackendMode.LOCAL) {
                ConexionSection(
                    ip = uiState.ip,
                    puerto = uiState.puerto,
                    onIpChange = viewModel::onIpChange,
                    onPuertoChange = viewModel::onPuertoChange,
                    onGuardarConexion = viewModel::onGuardarConexion,
                )
                SucursalSection(
                    sucursales = uiState.sucursales,
                    seleccionada = uiState.sucursalSeleccionada,
                    onSucursalSelected = viewModel::onSucursalSelected,
                )
            }
            Button(onClick = onNavigateToImportarCatalogo, modifier = Modifier.fillMaxWidth()) {
                Text("Importar catálogo")
            }
            Button(
                onClick = { if (viewModel.onIntentoAbrirConflictos()) onNavigateToConflictos() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Revisión de conflictos de sincronización")
            }
            AsistenteIaSection(
                activo = uiState.iaActivo,
                proveedor = uiState.iaProveedor,
                modelo = uiState.iaModelo,
                tokenInput = uiState.iaTokenInput,
                tieneTokenGuardado = uiState.iaTieneTokenGuardado,
                probandoConexion = uiState.iaProbandoConexion,
                resultadoPrueba = uiState.iaResultadoPrueba,
                onActivoChange = viewModel::onIaActivoChange,
                onProveedorSelected = viewModel::onIaProveedorSelected,
                onModeloChange = viewModel::onIaModeloChange,
                onTokenInputChange = viewModel::onIaTokenInputChange,
                onGuardar = viewModel::onGuardarIa,
                onProbarConexion = viewModel::onProbarConexionIa,
            )
            if (uiState.iaActivo) {
                PromptIaSection(
                    prompt = uiState.promptIa,
                    passwordInput = uiState.promptIaPasswordInput,
                    error = uiState.promptIaError,
                    guardando = uiState.promptIaGuardando,
                    onPromptChange = viewModel::onPromptIaChange,
                    onPasswordChange = viewModel::onPromptIaPasswordChange,
                    onGuardar = viewModel::onGuardarPromptIa,
                )
            }
        }
    }
}

@Composable
private fun ConexionSection(
    ip: String,
    puerto: String,
    onIpChange: (String) -> Unit,
    onPuertoChange: (String) -> Unit,
    onGuardarConexion: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Conexión al backend", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = ip,
            onValueChange = onIpChange,
            label = { Text("IP / Servidor") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = puerto,
            onValueChange = onPuertoChange,
            label = { Text("Puerto del servidor") },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AsistenteIaSection(
    activo: Boolean,
    proveedor: LlmProvider,
    modelo: String,
    tokenInput: String,
    tieneTokenGuardado: Boolean,
    probandoConexion: Boolean,
    resultadoPrueba: ApiResult<String>?,
    onActivoChange: (Boolean) -> Unit,
    onProveedorSelected: (LlmProvider) -> Unit,
    onModeloChange: (String) -> Unit,
    onTokenInputChange: (String) -> Unit,
    onGuardar: () -> Unit,
    onProbarConexion: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Asistente de IA", style = MaterialTheme.typography.titleMedium)
            Switch(checked = activo, onCheckedChange = onActivoChange)
        }
        if (activo) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                LlmProvider.entries.forEachIndexed { index, opcion ->
                    SegmentedButton(
                        selected = proveedor == opcion,
                        onClick = { onProveedorSelected(opcion) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = LlmProvider.entries.size),
                    ) {
                        Text(opcion.etiqueta())
                    }
                }
            }
            ModeloIaField(proveedor = proveedor, modelo = modelo, onModeloChange = onModeloChange)
            OutlinedTextField(
                value = tokenInput,
                onValueChange = onTokenInputChange,
                label = { Text("Token") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (tieneTokenGuardado) {
                Text("Token configurado", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onGuardar) { Text("Guardar") }
                Button(onClick = onProbarConexion, enabled = !probandoConexion) {
                    Text(if (probandoConexion) "Probando..." else "Probar conexión")
                }
            }
            when (resultadoPrueba) {
                is ApiResult.Success -> Text(
                    "Conexión exitosa (respuesta: ${resultadoPrueba.data})",
                    style = MaterialTheme.typography.bodyMedium,
                )
                is ApiResult.Error -> Text(
                    resultadoPrueba.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                null -> Unit
            }
        }
    }
}

private fun LlmProvider.etiqueta(): String = when (this) {
    LlmProvider.DEEP_SEEK -> "DeepSeek"
    LlmProvider.OPEN_AI -> "OpenAI"
    LlmProvider.OPEN_ROUTER -> "OpenRouter"
}

// Combo editable: permite elegir uno de los modelosSugeridos del proveedor
// (el modelo actual/default queda primero en esa lista) o escribir
// cualquier otro modelo a mano, para economizar tokens o mejorar el
// razonamiento segun el caso de uso (PLAN.md Parte 14, sub-paso 2).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeloIaField(
    proveedor: LlmProvider,
    modelo: String,
    onModeloChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val sugerencias = proveedor.modelosSugeridos.filter { it.contains(modelo, ignoreCase = true) }
    ExposedDropdownMenuBox(expanded = expanded && sugerencias.isNotEmpty(), onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = modelo,
            onValueChange = {
                onModeloChange(it)
                expanded = true
            },
            label = { Text("Modelo") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = expanded && sugerencias.isNotEmpty(), onDismissRequest = { expanded = false }) {
            sugerencias.forEach { sugerencia ->
                DropdownMenuItem(
                    text = { Text(sugerencia) },
                    onClick = {
                        onModeloChange(sugerencia)
                        expanded = false
                    },
                )
            }
        }
    }
}

// Prompt de sistema editable (PLAN.md Parte 16, sub-paso 1, cierra el gate
// bloqueante de la Parte 15 Decision 3): guardar un cambio exige reingresar
// la contrasena del usuario de la sesion activa - el campo de contrasena
// vive junto al de texto, no en un dialogo aparte, para que el costo de
// editar el prompt sea visible en la misma pantalla.
@Composable
private fun PromptIaSection(
    prompt: String,
    passwordInput: String,
    error: String?,
    guardando: Boolean,
    onPromptChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onGuardar: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Prompt de sistema de la IA", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            label = { Text("Prompt") },
            minLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = passwordInput,
            onValueChange = onPasswordChange,
            label = { Text("Contraseña (para confirmar el cambio)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onGuardar, enabled = !guardando && passwordInput.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(if (guardando) "Guardando..." else "Guardar prompt")
        }
    }
}
