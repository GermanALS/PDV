package com.pdv.pos.domain.repository

import com.pdv.pos.domain.model.Devolucion

interface DevolucionRepository {
    suspend fun registrarDevolucion(devolucion: Devolucion)
}
