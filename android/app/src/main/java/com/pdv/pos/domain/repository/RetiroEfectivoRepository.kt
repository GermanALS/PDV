package com.pdv.pos.domain.repository

import com.pdv.pos.domain.model.RetiroEfectivo
import kotlinx.coroutines.flow.Flow

interface RetiroEfectivoRepository {
    suspend fun registrarRetiro(retiro: RetiroEfectivo)
    fun observeRetiros(sucursalId: String): Flow<List<RetiroEfectivo>>

    // Un solo disparo por rango de fechas (sobre fecha), para la
    // exportacion de cortes/retiros por periodo (PLAN.md Parte 18,
    // sub-parte I).
    suspend fun obtenerRetirosDelPeriodo(sucursalId: String, desde: Long, hasta: Long): List<RetiroEfectivo>
}
