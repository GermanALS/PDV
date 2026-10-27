package com.pdv.pos

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.config.LimpiezaDataStore
import com.pdv.pos.config.SucursalInicial
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

    @Inject
    lateinit var limpiezaDataStore: LimpiezaDataStore

    @Inject
    lateinit var sucursalInicial: SucursalInicial

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
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
        applicationScope.launch {
            // Antes de restaurar: borra claves obsoletas como ia_token en
            // claro (PLAN.md Parte 37, M-10).
            limpiezaDataStore.limpiarClavesObsoletas()
            sessionManager.restaurarSesion(System.currentTimeMillis())
        }
        applicationScope.launch { sucursalInicial.preseleccionarSiFalta() }
        applicationScope.launch {
            configuracionPreferences.deviceConfig
                .map { it.backendMode }
                .distinctUntilChanged()
                .collect { syncScheduler.actualizarProgramacion(it) }
        }
    }
}
