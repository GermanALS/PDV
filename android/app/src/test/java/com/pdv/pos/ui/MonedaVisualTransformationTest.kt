package com.pdv.pos.ui

import androidx.compose.ui.text.AnnotatedString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MonedaVisualTransformationTest {

    @Test
    fun `el texto transformado antepone el prefijo de moneda`() {
        val resultado = MonedaVisualTransformation.filter(AnnotatedString("18.50"))

        assertEquals("$ 18.50", resultado.text.text)
    }

    @Test
    fun `originalToTransformed desplaza el cursor por el largo del prefijo`() {
        val mapeo = MonedaVisualTransformation.filter(AnnotatedString("18.50")).offsetMapping

        // Cursor al inicio del texto original (offset 0, antes de escribir
        // nada) cae despues del prefijo "$ " en el texto transformado, no
        // antes - de lo contrario el cursor saltaria al principio del campo
        // en vez de quedar junto al digito recien escrito.
        assertEquals(2, mapeo.originalToTransformed(0))
        assertEquals(7, mapeo.originalToTransformed(5))
    }

    @Test
    fun `transformedToOriginal descuenta el prefijo sin salirse del rango valido`() {
        val mapeo = MonedaVisualTransformation.filter(AnnotatedString("18.50")).offsetMapping

        assertEquals(0, mapeo.transformedToOriginal(2))
        assertEquals(5, mapeo.transformedToOriginal(7))
    }

    // Hallazgo esperado sin este clamp: un click dentro del prefijo "$ "
    // (offset transformado 0 o 1) mapearia a un offset negativo del texto
    // original, que crashea al posicionar el cursor.
    @Test
    fun `un click dentro del prefijo no produce un offset negativo`() {
        val mapeo = MonedaVisualTransformation.filter(AnnotatedString("18.50")).offsetMapping

        assertEquals(0, mapeo.transformedToOriginal(0))
        assertEquals(0, mapeo.transformedToOriginal(1))
    }

    @Test
    fun `un campo vacio solo muestra el prefijo`() {
        val resultado = MonedaVisualTransformation.filter(AnnotatedString(""))

        assertEquals("$ ", resultado.text.text)
        assertEquals(2, resultado.offsetMapping.originalToTransformed(0))
        assertEquals(0, resultado.offsetMapping.transformedToOriginal(2))
    }
}
