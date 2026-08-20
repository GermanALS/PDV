package com.pdv.pos.domain.repository

import com.pdv.pos.domain.model.RetiroEfectivo

interface RetiroEfectivoRepository {
    suspend fun registrarRetiro(retiro: RetiroEfectivo)
}
