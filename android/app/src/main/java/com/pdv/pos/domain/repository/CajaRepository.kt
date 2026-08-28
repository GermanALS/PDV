package com.pdv.pos.domain.repository

import com.pdv.pos.domain.model.CorteCaja
import com.pdv.pos.domain.model.TotalesCorte
import kotlinx.coroutines.flow.Flow

interface CajaRepository {
    suspend fun calcularTotales(sucursalId: String, fechaInicio: Long, fechaFin: Long): TotalesCorte
    suspend fun guardarCorte(corte: CorteCaja)
    fun observeCortes(sucursalId: String): Flow<List<CorteCaja>>

    // Un solo disparo por rango de fechas (sobre fechaFin), para la
    // exportacion de cortes/retiros por periodo (PLAN.md Parte 18,
    // sub-parte I). No acotado a los 7 dias que muestra el historial.
    suspend fun obtenerCortesDelPeriodo(sucursalId: String, desde: Long, hasta: Long): List<CorteCaja>
}
