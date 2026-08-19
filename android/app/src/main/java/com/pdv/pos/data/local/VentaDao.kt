package com.pdv.pos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Transaction

@Dao
interface VentaDao {

    @Insert
    suspend fun insertVenta(entity: VentaEntity)

    @Insert
    suspend fun insertDetalles(entities: List<VentaDetalleEntity>)

    @Insert
    suspend fun insertMovimientos(entities: List<MovimientoEntity>)

    // Venta + lineas + movimientos de salida en una sola transaccion Room
    // (PLAN.md Parte 7, "venta como evento aditivo").
    @Transaction
    suspend fun insertVentaCompleta(
        venta: VentaEntity,
        detalles: List<VentaDetalleEntity>,
        movimientos: List<MovimientoEntity>,
    ) {
        insertVenta(venta)
        insertDetalles(detalles)
        insertMovimientos(movimientos)
    }
}
