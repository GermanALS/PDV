package com.pdv.pos.domain.repository

import com.pdv.pos.domain.model.CorteCaja
import com.pdv.pos.domain.model.TotalesCorte

interface CajaRepository {
    suspend fun calcularTotales(sucursalId: String, fechaInicio: Long, fechaFin: Long): TotalesCorte
    suspend fun guardarCorte(corte: CorteCaja)
}
