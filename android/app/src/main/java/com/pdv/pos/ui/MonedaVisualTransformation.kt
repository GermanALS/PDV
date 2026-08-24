package com.pdv.pos.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

// Mascara de moneda (PLAN.md Parte 18, sub-parte C): antepone "$ " solo
// visualmente, sin tocar el valor real del campo (que sigue siendo el texto
// plano que ya se convierte a BigDecimal en cada pantalla) - sin separador
// de miles ni redondeo, consistente con que el resto de la app no formatea
// moneda con locale. Aplicar unicamente a campos de MONTO, nunca a los de
// CANTIDAD.
object MonedaVisualTransformation : VisualTransformation {
    private const val PREFIJO = "$ "

    override fun filter(text: AnnotatedString): TransformedText {
        val transformado = AnnotatedString(PREFIJO + text.text)
        val mapeo = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = offset + PREFIJO.length
            override fun transformedToOriginal(offset: Int): Int =
                (offset - PREFIJO.length).coerceIn(0, text.text.length)
        }
        return TransformedText(transformado, mapeo)
    }
}
