package com.pdv.pos.sync

import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import com.pdv.pos.sync.pull.EntityPuller
import com.pdv.pos.sync.push.EntityPusher
import com.pdv.pos.sync.push.PushResultado
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// Orquesta un ciclo del motor de sync diferido (PLAN.md Parte 32): primero
// push, luego pull. `pushers` viene ordenada de SyncModule (entradas antes
// que ventas y ajustes, para que los articulos creados offline existan en el
// backend cuando esos pushers los referencien). El pull esta acotado a la
// sucursal seleccionada: sincroniza con las demas terminales de la misma
// sucursal.
@Singleton
class DefaultSyncOrchestrator @Inject constructor(
    private val pushers: List<@JvmSuppressWildcards EntityPusher>,
    private val pullers: List<@JvmSuppressWildcards EntityPuller>,
    private val preferences: ConfiguracionPreferences,
    private val sessionManager: SessionManager,
    private val appLogger: AppLogger,
) : SyncOrchestrator {

    // Con la contrasena bootstrap pendiente el backend responde 403 a todo:
    // subir en ese estado marcaria cada fila como rechazada (PLAN.md Parte
    // 37, A-3), asi que el ciclo no arranca hasta el cambio.
    // Una sesion expirada por inactividad detiene el sync hasta el proximo
    // login (PLAN.md Parte 40, grupo 8).
    override suspend fun sincronizar(): SyncResultado {
        sessionManager.cerrarSiExpirada(System.currentTimeMillis())
        val session = sessionManager.session.value
        return when {
            session == null -> SyncResultado.Fallo(MOTIVO_SIN_SESION)
            session.debeCambiarPassword -> SyncResultado.Fallo(MOTIVO_CAMBIAR_PASSWORD)
            else -> sincronizarConSesionHabilitada()
        }
    }

    private suspend fun sincronizarConSesionHabilitada(): SyncResultado = try {
        var total = PushResultado.VACIO
        for (pusher in pushers) {
            total += pusher.empujarPendientes()
        }

        val sucursalId = preferences.deviceConfig.first().sucursalIdSeleccionada
        if (sucursalId != null) {
            for (puller in pullers) {
                puller.pull(sucursalId)
            }
        }

        appLogger.log(
            LogType.INFO,
            sucursalId = sucursalId ?: "-",
            usuario = "system",
            mensaje = "Sync OK: subidas=${total.subidas}, saltadas=${total.saltadas}, pull=${if (sucursalId != null) "si" else "sin sucursal"}",
        )
        SyncResultado.Exito
    } catch (e: IOException) {
        SyncResultado.Reintentar(e.message ?: "sin conexion")
    } catch (e: HttpException) {
        if (e.code() == 401) SyncResultado.Fallo(motivoNoAutorizado()) else SyncResultado.Reintentar("HTTP ${e.code()}")
    }

    // El 401 no cierra la sesion local (AuthInterceptor); el motivo distingue
    // un JWT vencido de una sesion que nunca lo tuvo (login con el backend
    // inalcanzable) - PLAN.md Parte 36, M-5.
    private fun motivoNoAutorizado(): String =
        if (sessionManager.session.value?.accessToken == null) MOTIVO_SIN_TOKEN else MOTIVO_RELOGIN
}

const val MOTIVO_RELOGIN = "sync requiere re-login"
const val MOTIVO_SIN_TOKEN = "la sesion no tiene token del backend: inicia sesion con el backend alcanzable"
const val MOTIVO_SIN_SESION = "no hay sesion activa: inicia sesion para sincronizar"
const val MOTIVO_CAMBIAR_PASSWORD ="cambia la contrasena inicial antes de sincronizar"
