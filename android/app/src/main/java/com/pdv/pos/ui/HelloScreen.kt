package com.pdv.pos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pdv.pos.data.remote.ApiResult

@Composable
fun HelloScreen(
    onNavigateToConfiguracion: () -> Unit,
    onNavigateToVenta: () -> Unit,
    onNavigateToEntrada: () -> Unit,
    onNavigateToInventario: () -> Unit,
    onNavigateToCaja: () -> Unit,
    viewModel: HelloViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(text = "Sesión: ${uiState.username}", style = MaterialTheme.typography.bodyMedium)
            Text(text = uiState.localGreeting, style = MaterialTheme.typography.titleLarge)

            val healthText = when (val result = uiState.healthResult) {
                null -> "Consultando backend..."
                is ApiResult.Success -> "Backend: ${result.data}"
                is ApiResult.Error -> "Error de backend: ${result.message}"
            }
            Text(text = healthText, style = MaterialTheme.typography.bodyLarge)

            Button(onClick = onNavigateToVenta, modifier = Modifier.fillMaxWidth()) {
                Text("Venta")
            }
            Button(onClick = onNavigateToEntrada, modifier = Modifier.fillMaxWidth()) {
                Text("Entrada de mercancía")
            }
            Button(onClick = onNavigateToInventario, modifier = Modifier.fillMaxWidth()) {
                Text("Inventario")
            }
            Button(onClick = onNavigateToCaja, modifier = Modifier.fillMaxWidth()) {
                Text("Caja")
            }
            Button(onClick = onNavigateToConfiguracion, modifier = Modifier.fillMaxWidth()) {
                Text("Configuración")
            }
            Button(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth()) {
                Text("Cerrar sesión")
            }
        }
    }
}
