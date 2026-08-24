package com.pdv.pos.inventario.importacion

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

private val TIPOS_MIME_CSV = arrayOf("text/csv", "text/comma-separated-values", "*/*")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportarCatalogoScreen(
    onBack: () -> Unit,
    viewModel: ImportacionCatalogoViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val seleccionarArchivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onArchivoSeleccionado)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Importar catálogo") },
                // Limpia el resultado de la importacion anterior al salir -
                // hiltViewModel() resuelve a la misma instancia mientras la
                // Activity viva (mismo patron que ChatViewModel, PLAN.md
                // Parte 16), asi que sin este reset el resumen/error de la
                // ultima importacion quedaba visible al reingresar a la
                // pantalla (hallazgo de pruebas en el Xiaomi).
                navigationIcon = {
                    Button(onClick = { viewModel.onResultadoDescartado(); onBack() }) { Text("Atrás") }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "El archivo debe ser un CSV con estas columnas, en este orden: " +
                    "SKU, Nombre, Categoria, Unidad de medida, Cantidad, Ubicacion, Precio de venta, Costo. " +
                    "Un artículo cuyo nombre o SKU ya exista en el catálogo suma la cantidad en vez de duplicarse.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { seleccionarArchivo.launch(TIPOS_MIME_CSV) },
                enabled = !uiState.procesando,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (uiState.procesando) "Importando..." else "Seleccionar archivo CSV")
            }

            if (uiState.procesando) {
                LinearProgressIndicator(
                    progress = { if (uiState.total == 0) 0f else uiState.procesados.toFloat() / uiState.total },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${uiState.procesados} de ${uiState.total}", style = MaterialTheme.typography.bodySmall)
            }

            if (uiState.encabezadoInvalido) {
                Text(
                    "El archivo no tiene las columnas esperadas. Revisa el encabezado e intenta de nuevo.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            uiState.mensaje?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            uiState.resumen?.let { resumen -> ResumenImportacionSection(resumen) }
        }
    }
}

@Composable
private fun ResumenImportacionSection(resumen: ResumenImportacion) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Importación completa", style = MaterialTheme.typography.titleMedium)
        Text("Artículos nuevos: ${resumen.creados}", style = MaterialTheme.typography.bodyMedium)
        Text("Artículos actualizados: ${resumen.actualizados}", style = MaterialTheme.typography.bodyMedium)
        Text("Filas con error: ${resumen.errores.size}", style = MaterialTheme.typography.bodyMedium)
        resumen.errores.forEach { error ->
            Text(
                "Fila ${error.numeroFila}: ${error.motivo}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
