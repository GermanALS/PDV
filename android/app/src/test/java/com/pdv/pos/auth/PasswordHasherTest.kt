package com.pdv.pos.auth

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PasswordHasherTest {

    private val hasher = PasswordHasher()

    @Test
    fun `hash never returns the plain password`() = runTest {
        val hash = hasher.hash("secret123")

        assertNotEquals("secret123", hash)
    }

    @Test
    fun `verify succeeds for the password that produced the hash`() = runTest {
        val hash = hasher.hash("secret123")

        assertTrue(hasher.verify("secret123", hash))
    }

    @Test
    fun `verify fails for a different password`() = runTest {
        val hash = hasher.hash("secret123")

        assertFalse(hasher.verify("otra-contrasena", hash))
    }

    // Interoperabilidad cross-lenguaje (PLAN.md Parte 13, Decision 2): un
    // hash bcrypt calculado por at.favre.lib debe ser el mismo formato que
    // el que genera bcrypt de Python (backend/app/security.py) - se prueba
    // aqui que el hash generado empieza con el prefijo bcrypt estandar.
    @Test
    fun `hash uses the standard bcrypt format`() = runTest {
        val hash = hasher.hash("secret123")

        assertTrue(hash.startsWith("\$2"))
    }
}
