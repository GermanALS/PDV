package com.pdv.pos.entrada

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pdv.pos.domain.model.Articulo
import com.pdv.pos.ui.EditableDropdownField
import com.pdv.pos.ui.MonedaVisualTransformation
import com.pdv.pos.venta.BarcodeScannerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntradaScreen(
    onBack: () -> Unit,
    viewModel: EntradaViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.escaneando != null) {
        BarcodeScannerDialog(
            onBarcodeScanned = viewModel::onBarcodeEscaneado,
            onDismiss = viewModel::onEscanerDismiss,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Entrada de mercancía") },
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
            TipoEntradaSection(tipo = uiState.tipo, onTipoChange = viewModel::onTipoChange)

            when (uiState.tipo) {
                TipoEntrada.ARTICULO_EXISTENTE -> ArticuloExistenteSection(
                    busqueda = uiState.busqueda,
                    articuloEncontrado = uiState.articuloEncontrado,
                    error = uiState.errorBusqueda,
                    onBusquedaChange = viewModel::onBusquedaChange,
                    onBuscar = viewModel::buscarArticuloExistente,
                    onEscanear = { viewModel.onEscanearClick(ObjetivoEscaneo.BUSQUEDA_EXISTENTE) },
                )
                TipoEntrada.ARTICULO_NUEVO -> ArticuloNuevoSection(
                    uiState = uiState,
                    viewModel = viewModel,
                )
            }

            DatosEntradaSection(
                cantidad = uiState.cantidad,
                ubicacion = uiState.ubicacion,
                ubicacionesDisponibles = uiState.ubicacionesDisponibles,
                onCantidadChange = viewModel::onCantidadChange,
                onUbicacionChange = viewModel::onUbicacionChange,
            )

            Button(onClick = viewModel::registrarEntrada, modifier = Modifier.fillMaxWidth()) {
                Text("Registrar entrada")
            }
            uiState.mensajeConfirmacion?.let { mensaje ->
                Text(mensaje, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TipoEntradaSection(tipo: TipoEntrada, onTipoChange: (TipoEntrada) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tipo de entrada", style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            TipoEntrada.entries.forEachIndexed { index, opcion ->
                SegmentedButton(
                    selected = tipo == opcion,
                    onClick = { onTipoChange(opcion) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = TipoEntrada.entries.size),
                ) {
                    Text(opcion.etiqueta())
                }
            }
        }
    }
}

private fun TipoEntrada.etiqueta(): String = when (this) {
    TipoEntrada.ARTICULO_NUEVO -> "Artículo nuevo"
    TipoEntrada.ARTICULO_EXISTENTE -> "Artículo existente"
}

@Composable
private fun ArticuloExistenteSection(
    busqueda: String,
    articuloEncontrado: Articulo?,
    error: String?,
    onBusquedaChange: (String) -> Unit,
    onBuscar: () -> Unit,
    onEscanear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Buscar artículo", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = busqueda,
            onValueChange = onBusquedaChange,
            label = { Text("Código de barras, SKU o nombre") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBuscar, modifier = Modifier.weight(1f)) { Text("Buscar") }
            Button(onClick = onEscanear, modifier = Modifier.weight(1f)) { Text("Escanear") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        articuloEncontrado?.let { articulo ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(articulo.nombre, style = MaterialTheme.typography.titleMedium)
                    Text("SKU: ${articulo.sku}", style = MaterialTheme.typography.bodyMedium)
                    Text("Código de barras: ${articulo.codigoBarras ?: "-"}", style = MaterialTheme.typography.bodyMedium)
                    Text("Precio: ${articulo.precioVenta}", style = MaterialTheme.typography.bodyMedium)
                    Text("Unidad: ${articulo.unidadMedida}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ArticuloNuevoSection(uiState: EntradaUiState, viewModel: EntradaViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Datos del artículo nuevo", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = uiState.codigoBarras,
                onValueChange = viewModel::onCodigoBarrasChange,
                label = { Text("Código de barras (opcional)") },
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { viewModel.onEscanearClick(ObjetivoEscaneo.CODIGO_BARRAS_NUEVO) }) {
                Text("Escanear")
            }
        }
        OutlinedTextField(
            value = uiState.sku,
            onValueChange = viewModel::onSkuChange,
            label = { Text("SKU") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = uiState.nombre,
            onValueChange = viewModel::onNombreChange,
            label = { Text("Nombre") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = uiState.descripcion,
            onValueChange = viewModel::onDescripcionChange,
            label = { Text("Descripción (opcional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        EditableDropdownField(
            label = "Categoría (opcional)",
            value = uiState.categoria,
            opciones = uiState.categoriasDisponibles,
            onValueChange = viewModel::onCategoriaChange,
            modifier = Modifier.fillMaxWidth(),
        )
        EditableDropdownField(
            label = "Unidad de medida",
            value = uiState.unidadMedida,
            opciones = uiState.unidadesMedidaDisponibles,
            onValueChange = viewModel::onUnidadMedidaChange,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = uiState.precioVenta,
            onValueChange = viewModel::onPrecioVentaChange,
            label = { Text("Precio de venta") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            visualTransformation = MonedaVisualTransformation,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = uiState.costo,
            onValueChange = viewModel::onCostoChange,
            label = { Text("Costo (opcional)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            visualTransformation = MonedaVisualTransformation,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DatosEntradaSection(
    cantidad: String,
    ubicacion: String,
    ubicacionesDisponibles: List<String>,
    onCantidadChange: (String) -> Unit,
    onUbicacionChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Datos de la entrada", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = cantidad,
                onValueChange = onCantidadChange,
                label = { Text("Cantidad") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            EditableDropdownField(
                label = "Estante / ubicación (opcional)",
                value = ubicacion,
                opciones = ubicacionesDisponibles,
                onValueChange = onUbicacionChange,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
