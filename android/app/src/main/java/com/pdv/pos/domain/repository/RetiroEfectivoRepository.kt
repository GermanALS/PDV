package com.pdv.pos.domain.repository

import com.pdv.pos.domain.model.RetiroEfectivo
import kotlinx.coroutines.flow.Flow

interface RetiroEfectivoRepository {
    suspend fun registrarRetiro(retiro: RetiroEfectivo)
    fun observeRetiros(sucursalId: String): Flow<List<RetiroEfectivo>>
}
