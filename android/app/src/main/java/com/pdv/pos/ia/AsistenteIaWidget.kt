package com.pdv.pos.ia

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

// Widget flotante accesible desde cualquier pantalla (PLAN.md Parte 16,
// sub-paso 1): se monta una sola vez en MainActivity, por fuera del
// `when(pantalla)` de navegacion manual, asi que sobrevive a los cambios de
// pantalla en vez de tener que agregarse a cada una. BoxScope porque
// necesita alinearse (BottomEnd) dentro del Box raiz de MainActivity.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.AsistenteIaWidget(viewModel: ChatViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    if (!uiState.visible) return

    FloatingActionButton(
        onClick = viewModel::abrirPanel,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(16.dp),
    ) {
        Text("IA")
    }

    if (uiState.abierto) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = viewModel::cerrarPanel, sheetState = sheetState) {
            when {
                // sinConexion (fallback automatico) tiene prioridad sobre
                // verFaq (acceso manual): si no hay conexion, "Volver al
                // chat" no seria un boton util - el proximo mensaje fallaria
                // igual. Los dos flags SI pueden quedar true a la vez (el
                // usuario abre Ayuda con un mensaje en vuelo que despues
                // falla por conexion) - reintentarConexion() limpia ambos
                // para no dejar al usuario atrapado en la vista de Ayuda
                // tras un "Reintentar" (hallazgo de code-reviewer).
                uiState.sinConexion -> FaqPanelContent(
                    titulo = "Chat no disponible sin conexión",
                    faqTexto = viewModel.faqTexto,
                    textoBoton = "Reintentar",
                    onCerrar = viewModel::reintentarConexion,
                )
                uiState.verFaq -> FaqPanelContent(
                    titulo = "Ayuda",
                    faqTexto = viewModel.faqTexto,
                    textoBoton = "Volver al chat",
                    onCerrar = viewModel::ocultarFaq,
                )
                else -> ChatPanelContent(uiState = uiState, viewModel = viewModel)
            }
        }
    }
}

// Fallback automatico (sub-paso 3, LlmClient reporta sin conexion) y acceso
// manual (sub-paso 4, boton de ayuda del diseno aprobado en el sub-paso 1)
// comparten esta misma vista - ambos reemplazan el panel entero por el FAQ
// empaquetado, solo cambia el titulo y a donde vuelve el boton.
@Composable
private fun FaqPanelContent(titulo: String, faqTexto: String, textoBoton: String, onCerrar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(480.dp),
    ) {
        Text(titulo, style = MaterialTheme.typography.titleMedium)
        Text(
            faqTexto,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 8.dp)
                .verticalScroll(rememberScrollState()),
        )
        Button(onClick = onCerrar, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(textoBoton)
        }
    }
}

@Composable
private fun ChatPanelContent(uiState: ChatUiState, viewModel: ChatViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(480.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Asistente de IA", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::limpiarHistorial, enabled = uiState.mensajes.isNotEmpty()) { Text("Borrar") }
                Button(onClick = viewModel::mostrarFaq) { Text("Ayuda") }
            }
        }
        val listState = rememberLazyListState()
        // Autoscroll al ultimo mensaje/tarjeta (hallazgo de pruebas en el
        // Xiaomi: el chat "se perdia" porque la lista no bajaba sola al
        // llegar contenido nuevo). El indicador "Escribiendo..." cuenta como
        // un item mas, asi que tambien queda visible mientras se espera
        // respuesta.
        val totalItems = uiState.mensajes.size + if (uiState.enviando) 1 else 0
        LaunchedEffect(totalItems) {
            if (totalItems > 0) listState.animateScrollToItem(totalItems - 1)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(uiState.mensajes, key = { it.id }) { mensaje ->
                ChatMensajeItem(mensaje = mensaje, onConfirmar = viewModel::confirmarAccion, onRechazar = viewModel::rechazarAccion)
            }
            if (uiState.enviando) {
                item { Text("Escribiendo...", style = MaterialTheme.typography.bodySmall) }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VoiceInputButton(onTranscripcion = viewModel::onTextoChange)
            OutlinedTextField(
                value = uiState.entradaTexto,
                onValueChange = viewModel::onTextoChange,
                placeholder = { Text("Escribí un mensaje...") },
                modifier = Modifier.weight(1f),
            )
            Button(onClick = viewModel::enviarMensaje, enabled = !uiState.enviando && uiState.entradaTexto.isNotBlank()) {
                Text("Enviar")
            }
        }
    }
}

@Composable
private fun ChatMensajeItem(
    mensaje: ChatUiMessage,
    onConfirmar: (String) -> Unit,
    onRechazar: (String) -> Unit,
) {
    when (mensaje) {
        is ChatUiMessage.DeUsuario -> Text("Tú: ${mensaje.texto}", style = MaterialTheme.typography.bodyMedium)
        is ChatUiMessage.DeIa -> Text("IA: ${mensaje.texto}", style = MaterialTheme.typography.bodyMedium)
        is ChatUiMessage.DeError -> Text(
            mensaje.texto,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        is ChatUiMessage.AccionPendiente -> Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(mensaje.descripcion, style = MaterialTheme.typography.bodyMedium)
                if (mensaje.resuelta) {
                    Text(mensaje.mensajeResultado.orEmpty(), style = MaterialTheme.typography.bodySmall)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onRechazar(mensaje.id) }) { Text("Rechazar") }
                        Button(onClick = { onConfirmar(mensaje.id) }) { Text("Confirmar") }
                    }
                }
            }
        }
    }
}
