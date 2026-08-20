package com.pdv.pos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.pdv.pos.sync.EventoAditivoCombiner
import java.math.BigDecimal
import java.util.UUID

@Dao
interface VentaDao {

    @Insert
    suspend fun insertVenta(entity: VentaEntity)

    @Insert
    suspend fun insertDetalles(entities: List<VentaDetalleEntity>)

    @Insert
    suspend fun insertMovimientos(entities: List<MovimientoEntity>)

    @Query("SELECT * FROM inventario WHERE sucursalId = :sucursalId AND articuloId = :articuloId LIMIT 1")
    suspend fun getInventario(sucursalId: String, articuloId: String): InventarioEntity?

    @Insert
    suspend fun insertInventario(entity: InventarioEntity)

    @Update
    suspend fun updateInventario(entity: InventarioEntity)

    // Venta + lineas + movimientos de salida, y el decremento real de
    // inventario.cantidad que cada movimiento de salida representa, en una
    // sola transaccion Room (PLAN.md Parte 7, "venta como evento aditivo";
    // el decremento en si era un gap corregido en la Parte 9 - antes solo se
    // escribia el movimiento, nunca se aplicaba a `inventario`). Mismo
    // patron de upsert con delta con signo via EventoAditivoCombiner que
    // EntradaDao.insertEntradaCompleta, nunca un UPDATE cantidad = X directo.
    @Transaction
    suspend fun insertVentaCompleta(
        venta: VentaEntity,
        detalles: List<VentaDetalleEntity>,
        movimientos: List<MovimientoEntity>,
        now: Long,
    ) {
        insertVenta(venta)
        insertDetalles(detalles)
        insertMovimientos(movimientos)

        movimientos.forEach { movimiento ->
            val deltaCantidad = movimiento.cantidad.negate()
            val existente = getInventario(movimiento.sucursalId, movimiento.articuloId)
            if (existente == null) {
                insertInventario(
                    InventarioEntity(
                        localId = UUID.randomUUID().toString(),
                        remoteId = null,
                        sucursalId = movimiento.sucursalId,
                        articuloId = movimiento.articuloId,
                        cantidad = EventoAditivoCombiner.combinar(BigDecimal.ZERO, deltaCantidad, BigDecimal.ZERO),
                        ubicacion = null,
                        updatedAt = now,
                        isSynced = false,
                        deletedAt = null,
                    ),
                )
            } else {
                updateInventario(
                    existente.copy(
                        cantidad = EventoAditivoCombiner.combinar(existente.cantidad, deltaCantidad, BigDecimal.ZERO),
                        updatedAt = now,
                        isSynced = false,
                    ),
                )
            }
        }
    }
}
