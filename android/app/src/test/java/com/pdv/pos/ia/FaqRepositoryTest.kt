package com.pdv.pos.ia

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

// PLAN.md Parte 20, sub-paso 2: valida el FAQ curado empaquetado
// (res/raw/faq.jsonl) y el armado del bloque de prompt. FaqRepository en si
// necesita un Context de Android, pero el parseo, el lookup y el armado de
// la instruccion son funciones puras testeables en la JVM; el test lee el
// recurso real desde el arbol de fuentes para que un faq.jsonl mal formado
// rompa aca y no recien en el dispositivo.
class FaqRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val entries: List<FaqEntry> =
        parseFaqJsonl(File("src/main/res/raw/faq.jsonl").readText(), json)

    @Test
    fun `faq_jsonl parsea sin error y tiene contenido suficiente`() {
        assertTrue(entries.size >= 35, "se esperaban >= 35 entradas, hay ${entries.size}")
        assertTrue(entries.all { it.question.isNotBlank() && it.answer.isNotBlank() })
    }

    @Test
    fun `la numeracion faq es contigua desde 1`() {
        assertEquals((1..entries.size).toList(), entries.map { it.faq })
    }

    @Test
    fun `buscarFaq devuelve la entrada por numero y null fuera de rango`() {
        val ultima = entries.last()
        assertEquals(ultima, buscarFaq(entries, ultima.faq))
        assertNull(buscarFaq(entries, 0))
        assertNull(buscarFaq(entries, entries.size + 1))
    }

    @Test
    fun `normalizarConsultaFaq quita acentos, mayusculas, signos y colapsa espacios`() {
        assertEquals("como cobro en efectivo", normalizarConsultaFaq("  ¿Cómo   cobro, en   efectivo?!  "))
    }

    @Test
    fun `buscarFaqPorTexto matchea la misma pregunta aunque falten signos y sobren acentos y mayusculas`() {
        val entry = entries.first()
        val comoLoEscribeElUsuario = "¿" + entry.question.uppercase().removeSuffix("?").replace("A", "Á") + "?"

        assertEquals(entry, buscarFaqPorTexto(entries, comoLoEscribeElUsuario))
    }

    @Test
    fun `buscarFaqPorTexto no matchea un parafraseo ni texto en blanco`() {
        assertNull(buscarFaqPorTexto(entries, "che, algo totalmente distinto que no esta en el faq"))
        assertNull(buscarFaqPorTexto(entries, "   "))
    }

    @Test
    fun `instruccionFaq empieza con el encabezado y lista todas las preguntas numeradas`() {
        val instruccion = construirInstruccionFaq(entries)
        assertTrue(instruccion.startsWith("# FAQ"))
        entries.forEach { entry ->
            assertTrue(
                instruccion.contains("${entry.faq}. ${entry.question}"),
                "falta la pregunta ${entry.faq} en instruccionFaq",
            )
        }
    }
}
