package com.pdv.pos.venta

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

// Imprime un ticket ya generado (TicketManager) via el Print Framework de
// Android (PLAN.md Parte 17, decision de arquitectura confirmada por el
// usuario: sin asumir un modelo de impresora concreto ni agregar una
// dependencia de terceros para ESC/POS). onImpresionEnviada se dispara en
// onWriteFinished, la unica senal confiable que expone el framework de que
// el documento ya se entrego al subsistema de impresion - Android no
// expone un evento de "ya salio el papel".
fun Context.imprimirTicket(archivo: File, folio: String, onImpresionEnviada: () -> Unit) {
    val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
    printManager.print(
        "ticket-$folio",
        TicketPrintDocumentAdapter(archivo, folio, onImpresionEnviada),
        PrintAttributes.Builder().build(),
    )
}

private class TicketPrintDocumentAdapter(
    private val archivo: File,
    private val folio: String,
    private val onImpresionEnviada: () -> Unit,
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder("ticket-$folio.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(1)
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        FileInputStream(archivo).use { entrada ->
            FileOutputStream(destination.fileDescriptor).use { salida ->
                entrada.copyTo(salida)
            }
        }
        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        onImpresionEnviada()
    }
}
