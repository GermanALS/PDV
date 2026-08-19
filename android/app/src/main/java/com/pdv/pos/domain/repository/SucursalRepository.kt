package com.pdv.pos.domain.repository

import com.pdv.pos.domain.model.Sucursal
import kotlinx.coroutines.flow.Flow

interface SucursalRepository {
    fun observeSucursales(): Flow<List<Sucursal>>
}
