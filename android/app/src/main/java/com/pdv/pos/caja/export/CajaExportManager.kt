package com.pdv.pos.caja.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.pdv.pos.domain.model.CorteCaja
import com.pdv.pos.domain.model.RetiroEfectivo
import com.pdv.pos.inventario.export.ArchivoExportado
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

// Espeja InventarioExportManager (PLAN.md Parte 9): escribe el CSV generado
// por CajaCsvExporter al cache de la app y expone un content:// Uri via
// FileProvider para el share sheet estandar, sin permisos de almacenamiento.
@Singleton
class CajaExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val timestampFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    suspend fun exportarCortesYRetiros(
        cortes: List<CorteCaja>,
        retiros: List<RetiroEfectivo>,
    ): ArchivoExportado = withContext(Dispatchers.IO) {
        val contenido = CajaCsvExporter.generar(cortes, retiros)
        val directorio = File(context.cacheDir, "exports").apply { mkdirs() }
        val archivo = File(directorio, "caja-cortes-${timestampFormat.format(Date())}.csv")
        archivo.writeText(contenido, Charsets.UTF_8)
        ArchivoExportado(uriDe(archivo), "text/csv")
    }

    private fun uriDe(archivo: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archivo)
}
