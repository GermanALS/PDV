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
    // sesion ya iniciada en esta ejecucion.
    suspend fun restaurarSesion() {
        if (_session.value == null) {
            _session.value = sessionStore.cargar()
        }
    }

    fun iniciarSesion(session: Session) {
        _session.value = session
        scope.launch { sessionStore.guardar(session) }
    }

    fun logout() {
        _session.value = null
        scope.launch { sessionStore.limpiar() }
    }
}
