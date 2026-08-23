package com.pdv.pos.ia

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.Locale

// Boton de comandos de voz (PLAN.md Parte 16, sub-paso 2): la voz es solo
// otra forma de llenar el mismo campo de texto del chat, no un subsistema
// aparte - onTranscripcion se cablea directo a ChatViewModel.onTextoChange
// desde el llamador, mismo patron que BarcodeScannerDialog.onBarcodeScanned
// (Parte 7) se cablea a un handler del ViewModel sin logica propia en el
// composable. Sin test unitario del reconocimiento en si (SpeechRecognizer/
// RecognitionListener son APIs de Android sin Robolectric en este proyecto)
// - mismo criterio que BarcodeAnalyzer/ML Kit: se verifica en el Xiaomi
// (needs-device, sub-paso 5), la logica de negocio que consume el texto
// resultante ya esta cubierta por ChatViewModelTest.
@Composable
fun VoiceInputButton(onTranscripcion: (String) -> Unit) {
    val context = LocalContext.current
    var tienePermiso by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var escuchando by remember { mutableStateOf(false) }
    val solicitarPermiso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { concedido ->
        tienePermiso = concedido
        if (concedido) escuchando = true
    }

    if (escuchando) {
        EscuchaDeVoz(
            onResultado = { texto ->
                escuchando = false
                onTranscripcion(texto)
            },
            onFin = { escuchando = false },
        )
    }

    Button(
        onClick = {
            if (tienePermiso) escuchando = true else solicitarPermiso.launch(Manifest.permission.RECORD_AUDIO)
        },
        enabled = !escuchando,
    ) {
        Text(if (escuchando) "Escuchando..." else "Voz")
    }
}

@Composable
private fun EscuchaDeVoz(onResultado: (String) -> Unit, onFin: () -> Unit) {
    val context = LocalContext.current
    val onResultadoState = rememberUpdatedState(onResultado)
    val onFinState = rememberUpdatedState(onFin)

    DisposableEffect(Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onFinState.value()
            return@DisposableEffect onDispose {}
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val texto = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (texto != null) onResultadoState.value(texto) else onFinState.value()
            }

            override fun onError(error: Int) = onFinState.value()
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        recognizer.startListening(intent)

        onDispose { recognizer.destroy() }
    }
}
