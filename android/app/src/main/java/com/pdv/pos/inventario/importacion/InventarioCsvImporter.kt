package com.pdv.pos.inventario.importacion

import com.pdv.pos.inventario.export.InventarioCsvExporter
import java.math.BigDecimal

data class DatosFilaImportada(
    val sku: String,
    val nombre: String,
    val categoria: String,
    val unidadMedida: String,
    val cantidad: BigDecimal,
    val ubicacion: String?,
    val precioVenta: BigDecimal,
    val costo: BigDecimal?,
)

sealed class FilaCsvImportada {
    data class Valida(val datos: DatosFilaImportada) : FilaCsvImportada()
    data class Invalida(val numeroFila: Int, val motivo: String) : FilaCsvImportada()
}

data class ResultadoParseoCsv(
    val encabezadoValido: Boolean,
    val filas: List<FilaCsvImportada>,
)

// Parser simetrico a InventarioCsvExporter (PLAN.md Parte 18, sub-parte A):
// mismas 8 columnas, mismo escapado RFC 4180 basico (comillas solo cuando el
// campo las necesita). Si el encabezado no coincide exactamente se aborta
// sin procesar ninguna fila - no hay forma segura de adivinar el mapeo de
// columnas cuando el formato no es el esperado.
object InventarioCsvImporter {

    fun parsear(contenido: String): ResultadoParseoCsv {
        val lineas = contenido.lines().filter { it.isNotBlank() }
        val encabezado = lineas.firstOrNull()?.let(::parsearLinea)?.map { it.trim() }
        if (encabezado != InventarioCsvExporter.ENCABEZADOS) {
            return ResultadoParseoCsv(encabezadoValido = false, filas = emptyList())
        }

        // Numero de fila 1-based contando el encabezado, para que el mensaje
        // de error coincida con lo que el usuario ve al abrir el CSV en un
        // editor de hojas de calculo.
        val filas = lineas.drop(1).mapIndexed { indice, linea ->
            parsearFila(numeroFila = indice + 2, campos = parsearLinea(linea))
        }
        return ResultadoParseoCsv(encabezadoValido = true, filas = filas)
    }

    private fun parsearFila(numeroFila: Int, campos: List<String>): FilaCsvImportada {
        val columnas = InventarioCsvExporter.ENCABEZADOS.size
        if (campos.size != columnas) {
            return FilaCsvImportada.Invalida(numeroFila, "Se esperaban $columnas columnas, la fila tiene ${campos.size}")
        }

        val sku = campos[0]
        val nombre = campos[1]
        val categoria = campos[2]
        val unidadMedida = campos[3]
        val cantidadTexto = campos[4]
        val ubicacionTexto = campos[5]
        val precioTexto = campos[6]
        val costoTexto = campos[7]

        if (sku.isBlank() || nombre.isBlank() || categoria.isBlank() || unidadMedida.isBlank()) {
            return FilaCsvImportada.Invalida(numeroFila, "SKU, Nombre, Categoria y Unidad de medida no pueden estar vacios")
        }

        val cantidad = cantidadTexto.toBigDecimalOrNull()
        if (cantidad == null || cantidad <= BigDecimal.ZERO) {
            return FilaCsvImportada.Invalida(numeroFila, "Cantidad invalida: \"$cantidadTexto\"")
        }

        val precioVenta = precioTexto.toBigDecimalOrNull()
        if (precioVenta == null) {
            return FilaCsvImportada.Invalida(numeroFila, "Precio de venta invalido: \"$precioTexto\"")
        }

        val costo = if (costoTexto.isBlank()) {
            null
        } else {
            costoTexto.toBigDecimalOrNull()
                ?: return FilaCsvImportada.Invalida(numeroFila, "Costo invalido: \"$costoTexto\"")
        }

        return FilaCsvImportada.Valida(
            DatosFilaImportada(
                sku = sku,
                nombre = nombre,
                categoria = categoria,
                unidadMedida = unidadMedida,
                cantidad = cantidad,
                ubicacion = ubicacionTexto.ifBlank { null },
                precioVenta = precioVenta,
                costo = costo,
            ),
        )
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? = runCatching { BigDecimal(trim()) }.getOrNull()

// Split RFC 4180 basico, simetrico a InventarioCsvExporter.escapar(): soporta
// campos entre comillas (con comillas dobles escapadas como "").
private fun parsearLinea(linea: String): List<String> {
    val campos = mutableListOf<String>()
    val actual = StringBuilder()
    var dentroDeComillas = false
    var i = 0
    while (i < linea.length) {
        val c = linea[i]
        when {
            dentroDeComillas && c == '"' && i + 1 < linea.length && linea[i + 1] == '"' -> {
                actual.append('"')
                i++
            }
            c == '"' -> dentroDeComillas = !dentroDeComillas
            c == ',' && !dentroDeComillas -> {
                campos.add(actual.toString())
                actual.clear()
            }
            else -> actual.append(c)
        }
        i++
    }
    campos.add(actual.toString())
    return campos
}
