package com.pdv.pos.domain.repository

import com.pdv.pos.domain.model.Usuario

sealed class LoginResultado {
    data class Exitoso(val usuario: Usuario, val accessToken: String?) : LoginResultado()
    data object CredencialesInvalidas : LoginResultado()
}

// Interfaz unica ModeAware (PLAN.md Parte 13, Decision 1), mismo patron que
// el resto de los repositorios del proyecto. Fallas de red/conexion se
// propagan como excepcion (IOException/HttpException), igual que los demas
// repositorios Remote - solo "credenciales incorrectas" es un resultado de
// negocio esperado, no una excepcion.
interface AuthRepository {
    suspend fun login(username: String, password: String): LoginResultado
}
