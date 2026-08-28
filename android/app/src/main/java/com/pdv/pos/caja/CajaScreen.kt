package com.pdv.pos.caja

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pdv.pos.domain.model.CorteCaja
import com.pdv.pos.inventario.export.ArchivoExportado
import com.pdv.pos.ui.MonedaVisualTransformation
import com.pdv.pos.ui.theme.success
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val formatoFechaHora = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CajaScreen(
    onBack: () -> Unit,
    viewModel: CajaViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.archivoExportado) {
        val archivo = uiState.archivoExportado ?: return@LaunchedEffect
        context.compartirExportacion(archivo)
        viewModel.onArchivoExportadoCompartido()
    }

    if (uiState.mostrarDialogoRetiro) {
        RetiroDialog(
            monto = uiState.montoRetiro,
            motivo = uiState.motivoRetiro,
            error = uiState.errorRetiro,
            onMontoChange = viewModel::onMontoRetiroChange,
            onMotivoChange = viewModel::onMotivoRetiroChange,
            onConfirmar = viewModel::onConfirmarRetiroClick,
            onCancelar = viewModel::onCancelarRetiroClick,
        )
    }

    if (uiState.mostrarDialogoExportar) {
        ExportarDialog(
            desde = uiState.exportarDesde,
            hasta = uiState.exportarHasta,
            exportando = uiState.exportando,
            onDesdeChange = viewModel::onExportarDesdeChange,
            onHastaChange = viewModel::onExportarHastaChange,
            onConfirmar = viewModel::onConfirmarExportarClick,
            onCancelar = viewModel::onCancelarExportarClick,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Caja") },
                navigationIcon = { Button(onClick = onBack) { Text("Atrás") } },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                TipoCorteSection(tipoCorte = uiState.tipoCorte, onTipoCorteChange = viewModel::onTipoCorteChange)
            }
            item {
                PeriodoSection(
                    fechaInicio = uiState.fechaInicio,
                    fechaFin = uiState.fechaFin,
                    editable = uiState.periodoEditable,
                    onFechaInicioChange = viewModel::onFechaInicioChange,
                    onFechaFinChange = viewModel::onFechaFinChange,
                )
            }
            item {
                Button(onClick = viewModel::onCalcularClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Calcular corte")
                }
            }
            if (uiState.calculado) {
                item {
                    ResultadoSection(
                        uiState = uiState,
                        onMontoContadoChange = viewModel::onMontoContadoChange,
                        onGuardarClick = viewModel::onGuardarClick,
                    )
                }
            }
            item {
                Button(onClick = viewModel::onRegistrarRetiroClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Registrar retiro de efectivo")
                }
            }
            item {
                OutlinedButton(onClick = viewModel::onExportarClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Exportar cortes")
                }
            }
            uiState.mensajeConfirmacion?.let { mensaje ->
                item { Text(mensaje, style = MaterialTheme.typography.bodyMedium) }
            }
            item {
                Text("Cortes de los últimos 7 días", style = MaterialTheme.typography.titleMedium)
            }
            items(uiState.historialCortes, key = { it.id }) { corte ->
                CorteCajaCard(corte)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TipoCorteSection(tipoCorte: TipoCorte, onTipoCorteChange: (TipoCorte) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tipo de corte", style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            TipoCorte.entries.forEachIndexed { index, opcion ->
                SegmentedButton(
                    selected = tipoCorte == opcion,
                    onClick = { onTipoCorteChange(opcion) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = TipoCorte.entries.size),
                ) {
                    Text(opcion.etiqueta())
                }
            }
        }
    }
}

private fun TipoCorte.etiqueta(): String = when (this) {
    TipoCorte.PARCIAL -> "Parcial"
    TipoCorte.FINAL -> "Final"
}

@Composable
private fun PeriodoSection(
    fechaInicio: Long,
    fechaFin: Long,
    editable: Boolean,
    onFechaInicioChange: (Long) -> Unit,
    onFechaFinChange: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (editable) {
            SelectorFechaHora(etiqueta = "Inicio del periodo", valorMillis = fechaInicio, onValorChange = onFechaInicioChange)
            SelectorFechaHora(etiqueta = "Fin del periodo", valorMillis = fechaFin, onValorChange = onFechaFinChange)
        } else {
            Text("Inicio del periodo: ${formatoFechaHora.format(Date(fechaInicio))}", style = MaterialTheme.typography.bodyMedium)
            Text("Fin del periodo: ${formatoFechaHora.format(Date(fechaFin))}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "El corte parcial calcula el periodo automaticamente: desde el ultimo corte del dia hasta ahora.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ResultadoSection(
    uiState: CajaUiState,
    onMontoContadoChange: (String) -> Unit,
    onGuardarClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Total ventas: ${uiState.totalVentas}", style = MaterialTheme.typography.bodyMedium)
        Text("Total efectivo: ${uiState.totalEfectivo}", style = MaterialTheme.typography.bodyMedium)
        Text("Total tarjeta: ${uiState.totalTarjeta}", style = MaterialTheme.typography.bodyMedium)
        Text("Total retiros: ${uiState.totalRetiros}", style = MaterialTheme.typography.bodyMedium)
        Text("Monto esperado en caja: ${uiState.montoEsperado}", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = uiState.montoContado,
            onValueChange = onMontoContadoChange,
            label = { Text("Monto contado") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            visualTransformation = MonedaVisualTransformation,
            modifier = Modifier.fillMaxWidth(),
        )
        uiState.diferencia?.let { diferencia ->
            val color = if (diferencia.compareTo(BigDecimal.ZERO) == 0) {
                MaterialTheme.success
            } else {
                MaterialTheme.colorScheme.error
            }
            Text("Diferencia: $diferencia", style = MaterialTheme.typography.bodyMedium, color = color)
        }
        Button(onClick = onGuardarClick, enabled = uiState.calculado, modifier = Modifier.fillMaxWidth()) {
            Text("Guardar corte")
        }
    }
}

@Composable
private fun RetiroDialog(
    monto: String,
    motivo: String,
    error: String?,
    onMontoChange: (String) -> Unit,
    onMotivoChange: (String) -> Unit,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Registrar retiro de efectivo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = monto,
                    onValueChange = onMontoChange,
                    label = { Text("Monto") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    visualTransformation = MonedaVisualTransformation,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = motivo,
                    onValueChange = onMotivoChange,
                    label = { Text("Motivo (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = onConfirmar) { Text("Registrar") } },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}

// Reusa el mismo SelectorFechaHora del corte final (PLAN.md Parte 18,
// sub-parte I). El rango arranca en los ultimos 7 dias y es editable a
// cualquier periodo; el fetch no esta acotado a esa ventana.
@Composable
private fun ExportarDialog(
    desde: Long,
    hasta: Long,
    exportando: Boolean,
    onDesdeChange: (Long) -> Unit,
    onHastaChange: (Long) -> Unit,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Exportar cortes y retiros") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Se exportan los cortes y los retiros del periodo elegido (por defecto, los últimos 7 días).",
                    style = MaterialTheme.typography.bodySmall,
                )
                SelectorFechaHora(etiqueta = "Desde", valorMillis = desde, onValorChange = onDesdeChange)
                SelectorFechaHora(etiqueta = "Hasta", valorMillis = hasta, onValorChange = onHastaChange)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmar, enabled = !exportando) {
                Text(if (exportando) "Exportando..." else "Exportar")
            }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}

@Composable
private fun CorteCajaCard(corte: CorteCaja) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${corte.tipo.replaceFirstChar { it.uppercase() }}: " +
                    "${formatoFechaHora.format(Date(corte.fechaInicio))} a ${formatoFechaHora.format(Date(corte.fechaFin))}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text("Total ventas: ${corte.totalVentas}", style = MaterialTheme.typography.bodyMedium)
            Text("Total retiros: ${corte.totalRetiros}", style = MaterialTheme.typography.bodyMedium)
            Text("Monto esperado: ${corte.montoEsperado}", style = MaterialTheme.typography.bodyMedium)
            Text("Monto contado: ${corte.montoContado ?: "-"}", style = MaterialTheme.typography.bodyMedium)
            Text("Diferencia: ${corte.diferencia ?: "-"}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorFechaHora(
    etiqueta: String,
    valorMillis: Long,
    onValorChange: (Long) -> Unit,
) {
    var mostrarFecha by remember { mutableStateOf(false) }
    var mostrarHora by remember { mutableStateOf(false) }
    var fechaElegidaMillis by remember { mutableStateOf(valorMillis) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(etiqueta, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { mostrarFecha = true }, modifier = Modifier.fillMaxWidth()) {
            Text(formatoFechaHora.format(Date(valorMillis)))
        }
    }

    if (mostrarFecha) {
        val estadoFecha = rememberDatePickerState(initialSelectedDateMillis = valorMillis)
        DatePickerDialog(
            onDismissRequest = { mostrarFecha = false },
            confirmButton = {
                TextButton(onClick = {
                    val seleccion = estadoFecha.selectedDateMillis
                    mostrarFecha = false
                    if (seleccion != null) {
                        fechaElegidaMillis = seleccion
                        mostrarHora = true
                    }
                }) { Text("Siguiente") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarFecha = false }) { Text("Cancelar") }
            },
        ) {
            DatePicker(state = estadoFecha)
        }
    }

    if (mostrarHora) {
        val calendarioActual = remember { Calendar.getInstance().apply { timeInMillis = valorMillis } }
        val estadoHora = rememberTimePickerState(
            initialHour = calendarioActual.get(Calendar.HOUR_OF_DAY),
            initialMinute = calendarioActual.get(Calendar.MINUTE),
        )
        AlertDialog(
            onDismissRequest = { mostrarHora = false },
            title = { Text("Selecciona la hora") },
            text = { TimePicker(state = estadoHora) },
            confirmButton = {
                TextButton(onClick = {
                    onValorChange(combinarFechaYHora(fechaElegidaMillis, estadoHora.hour, estadoHora.minute))
                    mostrarHora = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarHora = false }) { Text("Cancelar") }
            },
        )
    }
}

private fun Context.compartirExportacion(archivo: ArchivoExportado) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = archivo.mimeType
        putExtra(Intent.EXTRA_STREAM, archivo.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, "Exportar cortes de caja"))
}

// DatePicker entrega la fecha elegida como medianoche UTC; se extraen
// anio/mes/dia en UTC y se combinan con hora/minuto en la zona local para
// evitar que el desfase horario mueva el dia seleccionado.
private fun combinarFechaYHora(fechaMillisUtc: Long, hora: Int, minuto: Int): Long {
    val fechaUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = fechaMillisUtc }
    return Calendar.getInstance().apply {
        set(
            fechaUtc.get(Calendar.YEAR),
            fechaUtc.get(Calendar.MONTH),
            fechaUtc.get(Calendar.DAY_OF_MONTH),
            hora,
            minuto,
            0,
        )
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
