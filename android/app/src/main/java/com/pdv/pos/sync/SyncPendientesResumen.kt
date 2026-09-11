package com.pdv.pos.sync

import com.pdv.pos.sync.push.EntityPusher
import javax.inject.Inject
import javax.inject.Singleton

data class ResumenPendientes(
    // nombre de entidad -> cantidad pendiente, solo las que tienen > 0.
    val porEntidad: List<Pair<String, Int>>,
) {
    val total: Int get() = porEntidad.sumOf { it.second }
    val hayPendientes: Boolean get() = total > 0
}

// Cuenta lo que falta subir, para el dialogo de confirmacion del cambio
// de modo a/desde LOCAL_CON_SINCRONIZACION (PLAN.md Parte 32, Grupo 2,
// D3.2 - reemplaza el wiring de los 4 casos de MigrationPlanner).
@Singleton
class SyncPendientesResumen @Inject constructor(
    private val pushers: List<@JvmSuppressWildcards EntityPusher>,
) {
    suspend fun calcular(): ResumenPendientes = ResumenPendientes(
        porEntidad = pushers
            .map { it.nombre to it.contarPendientes() }
            .filter { it.second > 0 },
    )
}
