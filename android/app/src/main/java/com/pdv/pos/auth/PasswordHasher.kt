package com.pdv.pos.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// bcrypt, cost factor 12: mismo algoritmo y parametros que backend/app/security.py
// (PLAN.md Parte 13, Decision 2) - un hash calculado de un lado es
// verificable tal cual del otro. Cost factor 12 es deliberadamente lento
// (por diseno, para resistir fuerza bruta) - suspend + Dispatchers.Default
// evita bloquear el hilo principal, mismo criterio que TicketManager para
// trabajo CPU-bound.
private const val COST_FACTOR = 12

@Singleton
class PasswordHasher @Inject constructor() {

    suspend fun hash(plainPassword: String): String = withContext(Dispatchers.Default) {
        BCrypt.withDefaults().hashToString(COST_FACTOR, plainPassword.toCharArray())
    }

    suspend fun verify(plainPassword: String, passwordHash: String): Boolean = withContext(Dispatchers.Default) {
        BCrypt.verifyer().verify(plainPassword.toCharArray(), passwordHash).verified
    }
}
