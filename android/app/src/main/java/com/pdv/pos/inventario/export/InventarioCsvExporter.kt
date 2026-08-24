package com.pdv.pos.inventario.export

import com.pdv.pos.domain.model.InventarioItem
import com.pdv.pos.inventario.formatoCantidad

// Exportacion del inventario consultado (PLAN.md Parte 9, ampliacion
// aprobada explicitamente por el usuario) a texto plano - sin librerias de
// terceros, RFC 4180 basico (comillas solo cuando el campo las necesita).
object InventarioCsvExporter {

    // internal (no private): InventarioCsvImporter (PLAN.md Parte 18,
    // sub-parte A) valida el encabezado del CSV importado contra esta misma
    // lista, para que ambos lados del formato no puedan desalinearse.
    internal val ENCABEZADOS = listOf(
        "SKU", "Nombre", "Categoria", "Unidad de medida", "Cantidad", "Ubicacion", "Precio de venta", "Costo",
    )

    fun generar(items: List<InventarioItem>): String {
        val filas = items.map { it.toFila() }
        return (listOf(ENCABEZADOS) + filas).joinToString("\r\n") { fila ->
            fila.joinToString(",") { it.escapar() }
        } + "\r\n"
    }

    private fun InventarioItem.toFila(): List<String> = listOf(
        articulo.sku,
        articulo.nombre,
        articulo.categoria.orEmpty(),
        articulo.unidadMedida,
        cantidad.formatoCantidad(),
        ubicacion.orEmpty(),
        articulo.precioVenta.toPlainString(),
        articulo.costo?.toPlainString().orEmpty(),
    )

    private fun String.escapar(): String =
        if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${replace("\"", "\"\"")}\""
        } else {
            this
        }
}
