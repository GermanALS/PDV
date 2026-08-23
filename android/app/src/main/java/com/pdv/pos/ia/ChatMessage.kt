package com.pdv.pos.ia

import kotlinx.serialization.json.JsonPrimitive

// Modelo de UI del widget de chat (PLAN.md Parte 16, sub-paso 1) - separado
// de RespuestaIaDto/AccionIaDto (formato de transporte del LLM, Parte 15):
// una respuesta con acciones se despliega en varios ChatUiMessage (el texto
// de la IA + una AccionPendiente por accion propuesta), cada una con su
// propio ciclo de confirmar/rechazar independiente (Parte 15, Decision 2).
sealed class ChatUiMessage {
    abstract val id: String

    data class DeUsuario(override val id: String, val texto: String) : ChatUiMessage()
    data class DeIa(override val id: String, val texto: String) : ChatUiMessage()
    data class DeError(override val id: String, val texto: String) : ChatUiMessage()
    data class AccionPendiente(
        override val id: String,
        val descripcion: String,
        val accion: AccionIaDto,
        val resuelta: Boolean = false,
        val mensajeResultado: String? = null,
    ) : ChatUiMessage()
}

// Resumen legible de una accion propuesta para la tarjeta de confirmacion,
// antes de que el usuario decida - "parametros" es JsonObject sin tipar
// (Parte 15), asi que se listan sus pares clave/valor tal cual en vez de
// duplicar los DTOs privados de EjecutorAccionesIa.
fun descripcionAccion(accion: AccionIaDto): String {
    val etiqueta = when (accion.tipo) {
        "alta_articulo" -> "Alta de artículo"
        "corte_parcial" -> "Corte de caja parcial"
        "retiro_efectivo" -> "Retiro de efectivo"
        "registrar_devolucion" -> "Registro de devolución"
        else -> accion.tipo
    }
    val detalle = accion.parametros.entries.joinToString(separator = " · ") { (clave, valor) ->
        "$clave: ${(valor as? JsonPrimitive)?.content ?: valor.toString()}"
    }
    return if (detalle.isBlank()) etiqueta else "$etiqueta ($detalle)"
}
