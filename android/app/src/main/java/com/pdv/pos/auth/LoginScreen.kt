package com.pdv.pos.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var mostrarPanelConexion by remember { mutableStateOf(false) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(text = "Iniciar sesión", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = uiState.username,
                onValueChange = viewModel::onUsernameChange,
                label = { Text("Usuario") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("Contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            uiState.errorMessage?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }

            Button(onClick = viewModel::login, modifier = Modifier.fillMaxWidth()) {
                Text("Ingresar")
            }

            // Acceso a la configuracion de conexion sin sesion: si el
            // dispositivo quedo en modo REMOTO contra un backend inalcanzable,
            // desde aca se puede volver a LOCAL o corregir la URL sin quedar
            // encerrado fuera de la app (PLAN.md Parte 31). Cuando el ultimo
            // fallo fue de conectividad se resalta como accion primaria.
            if (uiState.connectivityError) {
                Button(
                    onClick = { mostrarPanelConexion = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Configurar conexión")
                }
            } else {
                TextButton(
                    onClick = { mostrarPanelConexion = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Configurar conexión")
                }
            }
        }
    }

    if (mostrarPanelConexion) {
        PanelConexionBottomSheet(
            onDismiss = {
                mostrarPanelConexion = false
                viewModel.onConexionConfigurada()
            },
        )
    }
}
