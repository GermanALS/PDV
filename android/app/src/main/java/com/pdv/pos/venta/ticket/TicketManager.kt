package com.pdv.pos.venta.ticket

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

// Genera el PDF del ticket en background (Dispatchers.IO) y lo persiste en
// filesDir/tickets/<fecha-de-la-venta>/ticket-<folio>.pdf - directorio
// persistente (no cacheDir) porque es un comprobante, mismo criterio que
// logs/ de AppLogger. Carpeta por fecha de creacion de la venta, no del
// dispositivo al momento de imprimir/reimprimir.
@Singleton
class TicketManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val carpetaFechaFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    suspend fun generarTicket(folio: String, fecha: Long, lineas: List<String>): File = withContext(Dispatchers.IO) {
        val carpeta = File(context.filesDir, "tickets/${carpetaFechaFormat.format(Date(fecha))}").apply { mkdirs() }
        val archivo = File(carpeta, "ticket-$folio.pdf")
        val documento = TicketPdfWriter.generar(lineas)
        FileOutputStream(archivo).use { documento.writeTo(it) }
        documento.close()
        archivo
    }
}
