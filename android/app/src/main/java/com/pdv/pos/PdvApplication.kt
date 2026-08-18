package com.pdv.pos

import android.app.Application
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PdvApplication : Application() {

    @Inject
    lateinit var appLogger: AppLogger

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        logSampleLinePerCategory()
    }

    // Verificacion manual de la Parte 5 (PLAN.md): ningun modulo real llama
    // todavia al logger (eso empieza en la Parte 6), asi que esta llamada
    // temporal genera una linea por categoria al arrancar para confirmar en
    // el Xiaomi que el archivo .txt las contiene. Remover cuando la Parte 6
    // empiece a loguear de verdad.
    private fun logSampleLinePerCategory() {
        applicationScope.launch {
            LogType.entries.forEach { tipo ->
                appLogger.log(tipo, sucursalId = "demo", usuario = "system", mensaje = "Verificacion Parte 5")
            }
        }
    }
}
