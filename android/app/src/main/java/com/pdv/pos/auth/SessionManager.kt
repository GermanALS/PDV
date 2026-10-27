package com.pdv.pos.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// Contenedor de la sesion activa (PLAN.md Parte 13): la validacion de
// credenciales vive en AuthRepository, no aqui - este objeto solo guarda el
// resultado. Reemplaza el login ficticio en memoria de la Parte 4.
//
// Desde la Parte 32 (D3) la sesion tambien se persiste cifrada via
// SessionStore para que el SyncWorker autentique tras un reinicio del
// proceso; la escritura al store es best-effort (no bloquea el login/logout
// en memoria) y el default SessionStore.NoOp deja el comportamiento anterior
// intacto para los tests que no la ejercen.
@Singleton
class SessionManager @Inject constructor(
    private val sessionStore: SessionStore,
) {

    // Conveniencia para tests que no ejercen la persistencia de sesion; en
    // produccion Hilt inyecta el DataStoreSessionStore real.
    constructor() : this(SessionStore.NoOp)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    // Se llama una vez al arrancar el proceso (PdvApplication). No pisa una
    // sesion ya iniciada en esta ejecucion. Una sesion persistida que lleva
    // mas de INACTIVIDAD_MAXIMA_MILLIS sin uso se descarta (M-5).
    suspend fun restaurarSesion(ahoraMillis: Long) {
        if (_session.value != null) return
        val guardada = sessionStore.cargar() ?: return
        if (expirada(ahoraMillis)) sessionStore.limpiar() else _session.value = guardada
    }

    /** Starts the inactivity window: called when the app goes to background. */
    fun registrarUso(ahoraMillis: Long) {
        if (_session.value != null) scope.launch { sessionStore.guardarUltimoUso(ahoraMillis) }
    }

    /** Logs out when the app went unused longer than INACTIVIDAD_MAXIMA_MILLIS (M-5). */
    suspend fun cerrarSiExpirada(ahoraMillis: Long) {
        if (_session.value != null && expirada(ahoraMillis)) logout()
    }

    // Sin marca de uso (sesion recien iniciada, nunca en segundo plano) no
    // hay ventana de inactividad abierta.
    private suspend fun expirada(ahoraMillis: Long): Boolean =
        sessionStore.ultimoUso()?.let { ahoraMillis - it > INACTIVIDAD_MAXIMA_MILLIS } ?: false

    fun iniciarSesion(session: Session) {
        _session.value = session
        scope.launch { sessionStore.guardar(session) }
    }

    fun logout() {
        _session.value = null
        scope.launch { sessionStore.limpiar() }
    }
}

// Decision "Expiracion de la sesion en modo LOCAL" (PLAN.md Parte 40, grupo
// 8): 12 horas en segundo plano. El tiempo en primer plano no cuenta.
const val INACTIVIDAD_MAXIMA_MILLIS = 12 * 60 * 60 * 1000L
