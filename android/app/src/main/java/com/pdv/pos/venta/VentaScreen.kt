package com.pdv.pos.venta

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pdv.pos.domain.model.Articulo
import java.io.File
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VentaScreen(
    onBack: () -> Unit,
    viewModel: VentaViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    if (uiState.mostrarEscaner) {
        BarcodeScannerDialog(
            onBarcodeScanned = viewModel::onBarcodeEscaneado,
            onDismiss = viewModel::onEscanerDismiss,
        )
    }

    if (uiState.mostrarDialogoEfectivo) {
        EfectivoRecibidoDialog(
            total = uiState.total,
            efectivoIngresado = uiState.efectivoIngresado,
            error = uiState.errorEfectivo,
            onEfectivoIngresadoChange = viewModel::onEfectivoIngresadoChange,
            onConfirmar = viewModel::confirmarEfectivo,
            onCancelar = viewModel::cancelarDialogoEfectivo,
        )
    }

    if (uiState.mostrarDialogoReimpresion) {
        val folio = uiState.ticketPdf?.folioDeTicket().orEmpty()
        AlertDialog(
            onDismissRequest = viewModel::onReimpresionDescartada,
            confirmButton = {
                TextButton(onClick = {
                    uiState.ticketPdf?.let { context.imprimirTicket(it, folio, onImpresionEnviada = {}) }
                    viewModel.onReimpresionDescartada()
                }) { Text("Sí, reimprimir") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onReimpresionDescartada) { Text("No") }
            },
            title = { Text("Reimpresión") },
            text = { Text("¿Reimprimir una copia del ticket para el cliente?") },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Venta de mostrador") },
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
                ArticuloEncontradoCard(articulo = articulo, onAgregar = viewModel::agregarAlCarrito)
            }
            CarritoSection(
                carrito = uiState.carrito,
                onCambiarCantidad = viewModel::cambiarCantidad,
                onQuitar = viewModel::quitarDelCarrito,
            )
            TotalesSection(
                subtotal = uiState.subtotal,
                descuento = uiState.descuento,
                impuestos = uiState.impuestos,
                total = uiState.total,
            )
            MetodoPagoSection(
                metodoPago = uiState.metodoPago,
                onMetodoPagoSelected = viewModel::onMetodoPagoSelected,
            )
            Button(
                onClick = viewModel::confirmarVenta,
                enabled = uiState.carrito.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Confirmar venta")
            }
            uiState.mensajeConfirmacion?.let { mensaje ->
                Text(mensaje, style = MaterialTheme.typography.bodyMedium)
            }
            uiState.cambioEntregado?.let { cambio ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Cambio a entregar: $cambio",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            if (uiState.mensajeConfirmacion != null) {
                AccionesPostVentaSection(
                    ticketDisponible = uiState.ticketPdf != null,
                    onImprimir = {
                        uiState.ticketPdf?.let { ticket ->
                            context.imprimirTicket(ticket, ticket.folioDeTicket()) { viewModel.onTicketImpreso() }
                        }
                    },
                    onCerrarVenta = viewModel::cerrarVenta,
                )
            }
        }
    }
}

private fun File.folioDeTicket(): String = nameWithoutExtension.removePrefix("ticket-")

// Mismo diseño de "botón doble" que MetodoPagoSection (SegmentedButton en
// SingleChoiceSegmentedButtonRow): aquí no representan una selección
// persistida, cada uno dispara su propia acción una vez - por eso
// `selected` queda siempre en false.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccionesPostVentaSection(
    ticketDisponible: Boolean,
    onImprimir: () -> Unit,
    onCerrarVenta: () -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        if (ticketDisponible) {
            SegmentedButton(
                selected = false,
                onClick = onImprimir,
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Imprimir ticket") }
            SegmentedButton(
                selected = false,
                onClick = onCerrarVenta,
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Cerrar venta") }
        } else {
            SegmentedButton(
                selected = false,
                onClick = onCerrarVenta,
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 1),
            ) { Text("Cerrar venta") }
        }
    }
}

@Composable
private fun EfectivoRecibidoDialog(
    total: BigDecimal,
    efectivoIngresado: String,
    error: String?,
    onEfectivoIngresadoChange: (String) -> Unit,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
) {
    val cambio = efectivoIngresado.trim().toBigDecimalOrNull()?.let { it - total }
    AlertDialog(
        onDismissRequest = onCancelar,
        confirmButton = { TextButton(onClick = onConfirmar) { Text("Confirmar") } },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
        title = { Text("Pago en efectivo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Total a cobrar: $total")
                OutlinedTextField(
                    value = efectivoIngresado,
                    onValueChange = onEfectivoIngresadoChange,
                    label = { Text("Efectivo recibido") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (cambio != null && cambio >= BigDecimal.ZERO) {
                    Text("Cambio: $cambio", style = MaterialTheme.typography.titleMedium)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
    )
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
        Text("Buscar artículo", style = MaterialTheme.typography.titleMedium)
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

@Composable
private fun ArticuloEncontradoCard(articulo: Articulo, onAgregar: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(articulo.nombre, style = MaterialTheme.typography.titleMedium)
            Text("SKU: ${articulo.sku}", style = MaterialTheme.typography.bodyMedium)
            Text("Código de barras: ${articulo.codigoBarras ?: "-"}", style = MaterialTheme.typography.bodyMedium)
            Text("Precio: ${articulo.precioVenta}", style = MaterialTheme.typography.bodyMedium)
            Text("Unidad: ${articulo.unidadMedida}", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onAgregar, modifier = Modifier.fillMaxWidth()) { Text("Agregar") }
        }
    }
}

@Composable
private fun CarritoSection(
    carrito: List<LineaCarrito>,
    onCambiarCantidad: (String, Int) -> Unit,
    onQuitar: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Carrito", style = MaterialTheme.typography.titleMedium)
        if (carrito.isEmpty()) {
            Text("Sin artículos agregados", style = MaterialTheme.typography.bodyMedium)
        }
        carrito.forEach { linea ->
            LineaCarritoRow(linea = linea, onCambiarCantidad = onCambiarCantidad, onQuitar = onQuitar)
        }
    }
}

@Composable
private fun LineaCarritoRow(
    linea: LineaCarrito,
    onCambiarCantidad: (String, Int) -> Unit,
    onQuitar: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Text(linea.articulo.nombre, style = MaterialTheme.typography.bodyLarge)
            Text(
                "Cant: ${linea.cantidad} x ${linea.articulo.precioVenta} = ${linea.subtotal}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedButton(onClick = { onCambiarCantidad(linea.articulo.id, linea.cantidad - 1) }) { Text("-") }
        OutlinedButton(onClick = { onCambiarCantidad(linea.articulo.id, linea.cantidad + 1) }) { Text("+") }
        OutlinedButton(onClick = { onQuitar(linea.articulo.id) }) { Text("Quitar") }
    }
}

@Composable
private fun TotalesSection(subtotal: BigDecimal, descuento: BigDecimal, impuestos: BigDecimal, total: BigDecimal) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Subtotal: $subtotal", style = MaterialTheme.typography.bodyMedium)
        Text("Descuento: $descuento", style = MaterialTheme.typography.bodyMedium)
        Text("Impuestos: $impuestos", style = MaterialTheme.typography.bodyMedium)
        Text("Total: $total", style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetodoPagoSection(metodoPago: MetodoPago, onMetodoPagoSelected: (MetodoPago) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Método de pago", style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            MetodoPago.entries.forEachIndexed { index, opcion ->
                SegmentedButton(
                    selected = metodoPago == opcion,
                    onClick = { onMetodoPagoSelected(opcion) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = MetodoPago.entries.size),
                ) {
                    Text(opcion.etiqueta())
                }
            }
        }
    }
}

private fun MetodoPago.etiqueta(): String = when (this) {
    MetodoPago.EFECTIVO -> "Efectivo"
    MetodoPago.TARJETA -> "Tarjeta"
}
