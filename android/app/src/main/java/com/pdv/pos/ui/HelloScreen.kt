package com.pdv.pos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pdv.pos.ui.theme.success
@Composable
private fun EncabezadoTurno(uiState: HelloUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = uiState.sucursalNombre ?: "Sin sucursal seleccionada",
                style = MaterialTheme.typography.titleLarge,
            )
            val rol = uiState.rolNombre?.let { " ($it)" }.orEmpty()
            Text(text = "En turno: ${uiState.username}$rol", style = MaterialTheme.typography.bodyLarge)
            uiState.conexion?.let { conexion ->
                val (texto, color) = when (conexion) {
                    EstadoConexion.SOLO_LOCAL -> "Modo local, sin servidor" to MaterialTheme.colorScheme.secondary
                    EstadoConexion.COMPROBANDO -> "Comprobando servidor…" to MaterialTheme.colorScheme.secondary
                    EstadoConexion.CONECTADO -> "Conectado al servidor" to MaterialTheme.success
                    EstadoConexion.SIN_CONEXION -> "Sin conexión con el servidor" to MaterialTheme.colorScheme.error
                }
                Text(text = texto, style = MaterialTheme.typography.bodyMedium, color = color)
            }
        }
    }
}

@Composable
fun HelloScreen(
    onNavigateToConfiguracion: () -> Unit,
    onNavigateToVenta: () -> Unit,
    onNavigateToEntrada: () -> Unit,
    onNavigateToInventario: () -> Unit,
    onNavigateToCaja: () -> Unit,
    onNavigateToDevoluciones: () -> Unit,
    onNavigateToUsuarios: () -> Unit,
    onNavigateToAcercaDe: () -> Unit,
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
            EncabezadoTurno(uiState)

            if ("venta" in uiState.modulosPermitidos) {
                Button(onClick = { if (viewModel.onIntentoNavegar("venta")) onNavigateToVenta() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Venta")
                }
            }
            if ("entrada" in uiState.modulosPermitidos) {
                Button(onClick = { if (viewModel.onIntentoNavegar("entrada")) onNavigateToEntrada() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Entrada de mercancía")
                }
            }
            if ("inventario" in uiState.modulosPermitidos) {
                Button(onClick = { if (viewModel.onIntentoNavegar("inventario")) onNavigateToInventario() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Inventario")
                }
            }
            if ("caja" in uiState.modulosPermitidos) {
                Button(onClick = { if (viewModel.onIntentoNavegar("caja")) onNavigateToCaja() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Caja")
                }
            }
            if ("devoluciones" in uiState.modulosPermitidos) {
                Button(onClick = { if (viewModel.onIntentoNavegar("devoluciones")) onNavigateToDevoluciones() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Devoluciones")
                }
            }
            if ("usuarios" in uiState.modulosPermitidos) {
                Button(onClick = { if (viewModel.onIntentoNavegar("usuarios")) onNavigateToUsuarios() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Usuarios")
                }
            }
            if ("configuracion" in uiState.modulosPermitidos) {
                Button(onClick = { if (viewModel.onIntentoNavegar("configuracion")) onNavigateToConfiguracion() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Configuración")
                }
            }
            OutlinedButton(onClick = onNavigateToAcercaDe, modifier = Modifier.fillMaxWidth()) {
                Text("Acerca de")
            }
            Button(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth()) {
                Text("Cerrar sesión")
            }
        }
    }
}
