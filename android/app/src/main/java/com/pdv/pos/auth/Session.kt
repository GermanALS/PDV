package com.pdv.pos.auth

// accessToken: JWT del login remoto (modo REMOTO). En modo LOCAL /
// LOCAL_CON_SINCRONIZACION es null - no hay backend que valide y
// AuthInterceptor no adjunta header. debeCambiarPassword: la sesion no da
// acceso a ningun modulo hasta cambiar la contrasena (PLAN.md Parte 37, A-3).
data class Session(
    val username: String,
    val usuarioId: String,
    val rolId: String,
    val accessToken: String? = null,
    val debeCambiarPassword: Boolean = false,
)
