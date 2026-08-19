package com.pdv.pos.domain.repository

import com.pdv.pos.domain.model.Venta

interface VentaRepository {
    suspend fun registrarVenta(venta: Venta)
}
