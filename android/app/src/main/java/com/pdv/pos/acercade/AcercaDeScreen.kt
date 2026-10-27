package com.pdv.pos.acercade

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pdv.pos.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcercaDeScreen(
    onBack: () -> Unit,
    viewModel: AcercaDeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Acerca de") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // drawable-night holds the dark-mode variant of the logo.
            Image(
                painter = painterResource(R.drawable.logo_mipe_eslogan),
                contentDescription = "MiPE PDV - Punto de venta",
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text("Versión: ${uiState.version}", style = MaterialTheme.typography.bodyLarge)
            Text("Fecha: ${uiState.fecha}", style = MaterialTheme.typography.bodyLarge)

            Text("Novedades", style = MaterialTheme.typography.titleMedium)
            uiState.novedades.forEach { novedad ->
                Text("- $novedad", style = MaterialTheme.typography.bodyMedium)
            }

            Text("Limitaciones conocidas", style = MaterialTheme.typography.titleMedium)
            uiState.limitaciones.forEach { limitacion ->
                Text("- $limitacion", style = MaterialTheme.typography.bodyMedium)
            }

            Text("Equipo de desarrollo", style = MaterialTheme.typography.titleMedium)
            Text(uiState.equipo, style = MaterialTheme.typography.bodyLarge)
            Text(uiState.contacto, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
