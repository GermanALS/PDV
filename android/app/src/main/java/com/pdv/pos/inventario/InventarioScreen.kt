package com.pdv.pos.inventario

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.content.Context
import android.content.Intent
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pdv.pos.domain.model.InventarioItem
import com.pdv.pos.inventario.export.ArchivoExportado
import com.pdv.pos.ui.EditableDropdownField
import com.pdv.pos.venta.BarcodeScannerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventarioScreen(
    onBack: () -> Unit,
    viewModel: InventarioViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.archivoParaCompartir) {
        val archivo = uiState.archivoParaCompartir ?: return@LaunchedEffect
        context.compartir(archivo)
        viewModel.onArchivoCompartido()
    }

    if (uiState.escaneando) {
        BarcodeScannerDialog(
            onBarcodeScanned = viewModel::onBarcodeEscaneado,
            onDismiss = viewModel::onEscanerDismiss,
        )
    }

    uiState.edicion?.let { edicion ->
        EdicionArticuloDialog(
            edicion = edicion,
            categoriasDisponibles = uiState.categoriasDisponibles,
            unidadesMedidaDisponibles = uiState.unidadesMedidaDisponibles,
            ubicacionesDisponibles = uiState.ubicacionesDisponibles,
            viewModel = viewModel,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inventario") },
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
            BusquedaSection(
                busqueda = uiState.busqueda,
                onBusquedaChange = viewModel::onBusquedaChange,
                onEscanear = viewModel::onEscanearClick,
            )

            TamanioPaginaSelector(
                tamanioPagina = uiState.tamanioPagina,
                onTamanioPaginaChange = viewModel::onTamanioPaginaChange,
            )

            ExportarSection(
                exportando = uiState.exportando,
                onExportarCsv = viewModel::exportarCsv,
                onExportarExcel = viewModel::exportarExcel,
            )

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.itemsPagina, key = { it.articulo.id }) { item ->
                    InventarioItemCard(item = item, onClick = { viewModel.onEditarClick(item) })
                }
            }

            PaginacionSection(
                paginaActual = uiState.paginaMostrada,
                totalPaginas = uiState.totalPaginas,
                onAnterior = viewModel::paginaAnterior,
                onSiguiente = viewModel::paginaSiguiente,
            )

            uiState.mensajeConfirmacion?.let { mensaje ->
                Text(mensaje, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ExportarSection(
    exportando: Boolean,
    onExportarCsv: () -> Unit,
    onExportarExcel: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Button(onClick = onExportarCsv, enabled = !exportando, modifier = Modifier.weight(1f)) {
            Text("Exportar CSV")
        }
        Button(onClick = onExportarExcel, enabled = !exportando, modifier = Modifier.weight(1f)) {
            Text("Exportar Excel")
        }
    }
}

private fun Context.compartir(archivo: ArchivoExportado) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = archivo.mimeType
        putExtra(Intent.EXTRA_STREAM, archivo.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, "Exportar inventario"))
}

@Composable
private fun BusquedaSection(
    busqueda: String,
    onBusquedaChange: (String) -> Unit,
    onEscanear: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = busqueda,
            onValueChange = onBusquedaChange,
            label = { Text("Nombre, SKU o código de barras") },
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onEscanear) { Text("Escanear") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TamanioPaginaSelector(
    tamanioPagina: Int,
    onTamanioPaginaChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Registros por página:", style = MaterialTheme.typography.bodyMedium)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = tamanioPagina.toString(),
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .width(96.dp)
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                TAMANIOS_PAGINA_DISPONIBLES.forEach { opcion ->
                    DropdownMenuItem(
                        text = { Text(opcion.toString()) },
                        onClick = {
                            onTamanioPaginaChange(opcion)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun InventarioItemCard(item: InventarioItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.articulo.nombre, style = MaterialTheme.typography.titleMedium)
            Text("SKU: ${item.articulo.sku}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Existencia: ${item.cantidad.formatoCantidad()} ${item.articulo.unidadMedida}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("Ubicación: ${item.ubicacion ?: "-"}", style = MaterialTheme.typography.bodyMedium)
            Text("Precio: ${item.articulo.precioVenta}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PaginacionSection(
    paginaActual: Int,
    totalPaginas: Int,
    onAnterior: () -> Unit,
    onSiguiente: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = onAnterior, enabled = paginaActual > 1) { Text("Anterior") }
        Text("Página $paginaActual de $totalPaginas", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onSiguiente, enabled = paginaActual < totalPaginas) { Text("Siguiente") }
    }
}

@Composable
private fun EdicionArticuloDialog(
    edicion: EdicionArticuloUiState,
    categoriasDisponibles: List<String>,
    unidadesMedidaDisponibles: List<String>,
    ubicacionesDisponibles: List<String>,
    viewModel: InventarioViewModel,
) {
    AlertDialog(
        onDismissRequest = viewModel::onEdicionDismiss,
        title = { Text("Editar artículo") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = edicion.nombre,
                    onValueChange = viewModel::onEdicionNombreChange,
                    label = { Text("Nombre") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = edicion.descripcion,
                    onValueChange = viewModel::onEdicionDescripcionChange,
                    label = { Text("Descripción (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                EditableDropdownField(
                    label = "Categoría (opcional)",
                    value = edicion.categoria,
                    opciones = categoriasDisponibles,
                    onValueChange = viewModel::onEdicionCategoriaChange,
                    modifier = Modifier.fillMaxWidth(),
                )
                EditableDropdownField(
                    label = "Unidad de medida",
                    value = edicion.unidadMedida,
                    opciones = unidadesMedidaDisponibles,
                    onValueChange = viewModel::onEdicionUnidadMedidaChange,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = edicion.precioVenta,
                    onValueChange = viewModel::onEdicionPrecioVentaChange,
                    label = { Text("Precio de venta") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = edicion.costo,
                    onValueChange = viewModel::onEdicionCostoChange,
                    label = { Text("Costo (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = edicion.cantidad,
                    onValueChange = viewModel::onEdicionCantidadChange,
                    label = { Text("Cantidad en existencia (actual: ${edicion.cantidadActual.formatoCantidad()})") },
                    modifier = Modifier.fillMaxWidth(),
                )
                EditableDropdownField(
                    label = "Estante / ubicación (opcional)",
                    value = edicion.ubicacion,
                    opciones = ubicacionesDisponibles,
                    onValueChange = viewModel::onEdicionUbicacionChange,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "El cambio de cantidad se registra como ajuste de inventario, no sobrescribe el valor directamente.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::guardarCambios) { Text("Guardar cambios") }
        },
        dismissButton = {
            TextButton(onClick = viewModel::onEdicionDismiss) { Text("Cancelar") }
        },
    )
}
