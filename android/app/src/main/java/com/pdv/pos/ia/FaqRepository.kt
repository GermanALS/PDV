package com.pdv.pos.ia

import android.content.Context
import com.pdv.pos.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

// Una entrada del FAQ curado (PLAN.md Parte 20): res/raw/faq.jsonl, una
// linea JSON por entrada. "faq" es la clave de lookup - la escribe el
// modelo en la accion "consultar_faq" y el usuario en el atajo "qN".
@Serializable
data class FaqEntry(
    val faq: Int,
    val question: String,
    val answer: String,
)

// Carga el FAQ empaquetado (reemplaza a FaqContent de la Parte 16): lo
// parsea una sola vez al construirse, expone lookup por numero, el texto
// legible para el panel de ayuda, y el bloque que se concatena al prompt
// de sistema en runtime. Sin Room ni red - solo lee el recurso, mismo
// patron de Context inyectado que TicketManager/InventarioExportManager.
@Singleton
class FaqRepository @Inject constructor(
    @ApplicationContext context: Context,
    json: Json,
) {
    val entries: List<FaqEntry> = parseFaqJsonl(
        context.resources.openRawResource(R.raw.faq).bufferedReader().use { it.readText() },
        json,
    )

    fun find(numero: Int): FaqEntry? = buscarFaq(entries, numero)

    // Match exacto tras normalizar (PLAN.md Parte 20, ronda de dispositivo
    // 2026-08-30): en el celular cuesta escribir "¿", asi que la pregunta
    // del usuario no coincide literal con la del FAQ y el modelo terminaba
    // improvisando una respuesta ademas del texto verbatim. Esto la matchea
    // antes de llamar al LLM (cero tokens, igual que "qN"). No cubre
    // parafraseos - eso es el trabajo futuro de embeddings.
    fun matchPorTexto(consulta: String): FaqEntry? = buscarFaqPorTexto(entries, consulta)

    // Texto plano legible para el panel de Ayuda y para el fallback sin
    // conexion (sub-paso 6) - reemplaza al Markdown libre de faq.md.
    val textoAyuda: String = entries.joinToString(separator = "\n\n") { "P: ${it.question}\nR: ${it.answer}" }

    // Se concatena al prompt de sistema en ChatViewModel, igual que
    // FORMATO_SALIDA_ACCIONES: instruye a responder via "consultar_faq" y
    // lista las preguntas numeradas para que el modelo elija por numero.
    val instruccionFaq: String = construirInstruccionFaq(entries)
}

fun parseFaqJsonl(contenido: String, json: Json): List<FaqEntry> =
    contenido.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { json.decodeFromString<FaqEntry>(it) }
        .toList()

fun buscarFaq(entries: List<FaqEntry>, numero: Int): FaqEntry? =
    entries.firstOrNull { it.faq == numero }

fun buscarFaqPorTexto(entries: List<FaqEntry>, consulta: String): FaqEntry? {
    val normalizada = normalizarConsultaFaq(consulta)
    if (normalizada.isBlank()) return null
    return entries.firstOrNull { normalizarConsultaFaq(it.question) == normalizada }
}

// Minusculas, sin acentos, sin signos de interrogacion/exclamacion ni
// puntuacion, espacios colapsados. Hace converger "¿Cómo cobro en
// efectivo?" y "como cobro en efectivo" a la misma cadena.
fun normalizarConsultaFaq(texto: String): String =
    Normalizer.normalize(texto.trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[¿?¡!.,;:\"'()]+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

fun construirInstruccionFaq(entries: List<FaqEntry>): String = buildString {
    append(CABECERA_INSTRUCCION_FAQ)
    entries.forEach { append("\n${it.faq}. ${it.question}") }
}

private val CABECERA_INSTRUCCION_FAQ = """
# FAQ

Tu seccion de FAQ contiene respuestas a las preguntas frecuentes sobre el
sistema. Abajo hay una lista de esas preguntas con su numero. Si el
mensaje del usuario coincide con una de ellas, usa la accion de solo
lectura "consultar_faq" con ese numero en vez de responder de memoria: la
app devuelve la respuesta tal cual, con sus marcadores de formato y
hyperlinks originales. En "respuesta_usuario" alcanza con una frase breve
que la introduzca, no la copies entera.
""".trimIndent()
