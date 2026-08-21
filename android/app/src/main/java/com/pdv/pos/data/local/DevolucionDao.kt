package com.pdv.pos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Transaction

@Dao
interface DevolucionDao {

    @Insert
    suspend fun insertDevolucion(entity: DevolucionEntity)

    @Insert
    suspend fun insertDetalles(entities: List<DevolucionDetalleEntity>)

    // A diferencia de VentaDao/EntradaDao, esta transaccion no toca
    // `inventario` ni escribe `movimiento`: el checklist de la Parte 11 no
    // lo pide - una devolucion aqui es para "gestionar con el proveedor",
    // no un restock inmediato del catalogo vendible.
    @Transaction
    suspend fun insertDevolucionCompleta(devolucion: DevolucionEntity, detalles: List<DevolucionDetalleEntity>) {
        insertDevolucion(devolucion)
        insertDetalles(detalles)
    }
}
