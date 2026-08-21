package com.pdv.pos.devolucion

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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pdv.pos.domain.model.Articulo
import com.pdv.pos.venta.BarcodeScannerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevolucionScreen(
    onBack: () -> Unit,
    viewModel: DevolucionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.mostrarEscaner) {
        BarcodeScannerDialog(
            onBarcodeScanned = viewModel::onBarcodeEscaneado,
            onDismiss = viewModel::onEscanerDismiss,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Devoluciones") },
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
            BusquedaSection(
                busqueda = uiState.busqueda,
                error = uiState.errorBusqueda,
                onBusquedaChange = viewModel::onBusquedaChange,
                onBuscar = viewModel::buscar,
                onEscanear = viewModel::onEscanearClick,
            )
            uiState.articuloEncontrado?.let { articulo ->
                ArticuloEncontradoCard(
                    articulo = articulo,
                    cantidad = uiState.cantidadIngresada,
                    motivo = uiState.motivoIngresado,
                    condicion = uiState.condicionSeleccionada,
                    error = uiState.errorLinea,
                    onCantidadChange = viewModel::onCantidadChange,
                    onMotivoChange = viewModel::onMotivoChange,
                    onCondicionSelected = viewModel::onCondicionSelected,
                    onAgregar = viewModel::agregarLinea,
                )
            }
            LineasSection(lineas = uiState.lineas, onQuitar = viewModel::quitarLinea)
            OutlinedTextField(
                value = uiState.ventaOriginal,
                onValueChange = viewModel::onVentaOriginalChange,
                label = { Text("Folio de venta original (opcional)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = viewModel::registrarDevolucion,
                enabled = uiState.lineas.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Registrar devolución")
            }
            uiState.mensajeConfirmacion?.let { mensaje ->
                Text(mensaje, style = MaterialTheme.typography.bodyMedium)
            }
            HistorialSection(historial = uiState.historial)
        }
    }
}

@Composable
private fun BusquedaSection(
    busqueda: String,
    error: String?,
    onBusquedaChange: (String) -> Unit,
    onBuscar: () -> Unit,
    onEscanear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Buscar artículo a devolver", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = busqueda,
            onValueChange = onBusquedaChange,
            label = { Text("Código de barras, SKU o nombre") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBuscar, modifier = Modifier.weight(1f)) { Text("Buscar") }
            OutlinedButton(onClick = onEscanear, modifier = Modifier.weight(1f)) { Text("Escanear") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticuloEncontradoCard(
    articulo: Articulo,
    cantidad: String,
    motivo: String,
    condicion: Condicion,
    error: String?,
    onCantidadChange: (String) -> Unit,
    onMotivoChange: (String) -> Unit,
    onCondicionSelected: (Condicion) -> Unit,
    onAgregar: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(articulo.nombre, style = MaterialTheme.typography.titleMedium)
            Text("SKU: ${articulo.sku}", style = MaterialTheme.typography.bodyMedium)
            Text("Unidad: ${articulo.unidadMedida}", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = cantidad,
                onValueChange = onCantidadChange,
                label = { Text("Cantidad a devolver") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = motivo,
                onValueChange = onMotivoChange,
                label = { Text("Motivo (opcional)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Condición", style = MaterialTheme.typography.bodyMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                Condicion.entries.forEachIndexed { index, opcion ->
                    SegmentedButton(
                        selected = condicion == opcion,
                        onClick = { onCondicionSelected(opcion) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = Condicion.entries.size),
                    ) {
                        Text(opcion.etiqueta())
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = onAgregar, modifier = Modifier.fillMaxWidth()) { Text("Agregar a la devolución") }
        }
    }
}

private fun Condicion.etiqueta(): String = when (this) {
    Condicion.DEFECTUOSO -> "Defectuoso"
    Condicion.NO_DEFECTUOSO -> "No defectuoso"
}

@Composable
private fun LineasSection(lineas: List<LineaDevolucion>, onQuitar: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Líneas de la devolución", style = MaterialTheme.typography.titleMedium)
        if (lineas.isEmpty()) {
            Text("Sin artículos agregados", style = MaterialTheme.typography.bodyMedium)
        }
        lineas.forEach { linea ->
            LineaDevolucionRow(linea = linea, onQuitar = onQuitar)
        }
    }
}

@Composable
private fun LineaDevolucionRow(linea: LineaDevolucion, onQuitar: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Text(linea.articulo.nombre, style = MaterialTheme.typography.bodyLarge)
            Text(
                "Cant: ${linea.cantidad} · ${linea.condicion.etiqueta()}${linea.motivo?.let { " · $it" } ?: ""}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedButton(onClick = { onQuitar(linea.id) }) { Text("Quitar") }
    }
}

@Composable
private fun HistorialSection(historial: List<DevolucionRegistrada>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Historial de esta sesión (para gestión con proveedor)", style = MaterialTheme.typography.titleMedium)
        if (historial.isEmpty()) {
            Text("Sin devoluciones registradas todavía", style = MaterialTheme.typography.bodyMedium)
        }
        historial.forEach { registro ->
            Text(
                "${registro.folio} · ${registro.cantidadLineas} línea(s) · ${registro.estado}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
