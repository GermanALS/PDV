package com.pdv.pos.ia

data class ChatUiState(
    val visible: Boolean = false,
    val abierto: Boolean = false,
    val mensajes: List<ChatUiMessage> = emptyList(),
    val entradaTexto: String = "",
    val enviando: Boolean = false,
    // Deteccion reactiva, no proactiva (PLAN.md Parte 16, sub-paso 3): se
    // activa cuando LlmClient devuelve MENSAJE_SIN_CONEXION_IA, no por un
    // chequeo previo de conectividad. Mientras esta en true, el panel
    // muestra el FAQ empaquetado en vez del chat.
    val sinConexion: Boolean = false,
    // Acceso manual al FAQ (PLAN.md Parte 16, sub-paso 4, diseno aprobado
    // del sub-paso 1: boton de ayuda en el header) - independiente de
    // sinConexion, que es el fallback automatico.
    val verFaq: Boolean = false,
)
