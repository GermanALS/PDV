package com.pdv.pos.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Login ficticio de la Parte 4 (PLAN.md). Se reemplaza por completo en la
 * Parte 13 junto con las credenciales admin/password y user1/password.
 */
@Singleton
class SessionManager @Inject constructor() {

    private val fakeUsers = mapOf(
        "admin" to "password",
        "user1" to "password",
    )

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    fun login(username: String, password: String): Boolean {
        val isValid = fakeUsers[username] == password
        if (isValid) {
            _session.value = Session(username)
        }
        return isValid
    }

    fun logout() {
        _session.value = null
    }
}
