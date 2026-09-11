package com.pdv.pos

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import com.pdv.pos.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PdvApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var appLogger: AppLogger

    // WorkManager con inicializacion on-demand: HiltWorkerFactory inyecta las
    // dependencias de los @HiltWorker (SyncWorker, PLAN.md Parte 32). El
    // inicializador por defecto se quita en AndroidManifest.xml.
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncScheduler: SyncScheduler

    @Inject
    lateinit var configuracionPreferences: ConfiguracionPreferences

    @Inject
    lateinit var sessionManager: SessionManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        logSampleLinePerCategory()
        restaurarSesionYProgramarSync()
    }

    // Al arrancar: restaura la sesion persistida (PLAN.md Parte 32, D3) y
    // mantiene el SyncWorker periodico alineado con el BackendMode - colecta
    // el modo, asi el cambio en caliente desde Configuracion lo reprograma
    // (encola en LOCAL_CON_SINCRONIZACION, cancela en LOCAL/REMOTO) sin que
    // Configuracion dependa del scheduler. Si MainActivity compone antes de
    // que termine la restauracion se ve un parpadeo breve de LoginScreen;
    // se revisa en el Grupo 5 si molesta.
    private fun restaurarSesionYProgramarSync() {
        applicationScope.launch { sessionManager.restaurarSesion() }
        applicationScope.launch {
            configuracionPreferences.deviceConfig
                .map { it.backendMode }
                .distinctUntilChanged()
                .collect { syncScheduler.actualizarProgramacion(it) }
        }
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
