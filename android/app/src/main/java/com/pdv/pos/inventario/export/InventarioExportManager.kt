package com.pdv.pos.inventario.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.pdv.pos.domain.model.InventarioItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class ArchivoExportado(val uri: Uri, val mimeType: String)

// Escribe el CSV/XLSX generado por InventarioCsvExporter/InventarioExcelExporter
// al cache de la app y expone un content:// Uri via FileProvider (PLAN.md
// Parte 9, ampliacion de exportacion) - el archivo se comparte con el share
// sheet estandar de Android, sin pedir permisos de almacenamiento.
@Singleton
class InventarioExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val timestampFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    suspend fun exportarCsv(items: List<InventarioItem>): ArchivoExportado = withContext(Dispatchers.IO) {
        val contenido = InventarioCsvExporter.generar(items)
        val archivo = nuevoArchivo("csv")
        archivo.writeText(contenido, Charsets.UTF_8)
        ArchivoExportado(uriDe(archivo), "text/csv")
    }

    suspend fun exportarExcel(items: List<InventarioItem>): ArchivoExportado = withContext(Dispatchers.IO) {
        val contenido = InventarioExcelExporter.generar(items)
        val archivo = nuevoArchivo("xlsx")
        archivo.writeBytes(contenido)
        ArchivoExportado(
            uriDe(archivo),
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        )
    }

    private fun nuevoArchivo(extension: String): File {
        val directorio = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(directorio, "inventario-${timestampFormat.format(Date())}.$extension")
    }

    private fun uriDe(archivo: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archivo)
}
