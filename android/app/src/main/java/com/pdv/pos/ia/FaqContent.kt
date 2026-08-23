package com.pdv.pos.ia

import android.content.Context
import com.pdv.pos.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

// FAQ empaquetado como recurso offline de la app (PLAN.md Parte 16,
// sub-paso 4): res/raw/faq.md, disponible sin conexion. Sirve como ayuda
// estatica y como fallback del chat cuando no hay conexion al proveedor de
// IA (sub-paso 3) - mismo patron de Context inyectado que TicketManager/
// InventarioExportManager.
class FaqContent @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun texto(): String = context.resources.openRawResource(R.raw.faq).bufferedReader().use { it.readText() }
}
