package com.pdv.pos.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// Contenedor de la sesion activa (PLAN.md Parte 13): la validacion de
// credenciales vive en AuthRepository, no aqui - este objeto solo guarda el
// resultado. Reemplaza el login ficticio en memoria de la Parte 4.
@Singleton
class SessionManager @Inject constructor() {

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    fun iniciarSesion(session: Session) {
        _session.value = session
    }

    fun logout() {
        _session.value = null
    }
}
