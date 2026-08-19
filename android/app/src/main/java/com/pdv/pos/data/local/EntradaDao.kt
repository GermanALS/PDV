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
interface EntradaDao {

    @Insert
    suspend fun insertArticulo(entity: ArticuloEntity)

    @Query("SELECT * FROM inventario WHERE sucursalId = :sucursalId AND articuloId = :articuloId LIMIT 1")
    suspend fun getInventario(sucursalId: String, articuloId: String): InventarioEntity?

    @Insert
    suspend fun insertInventario(entity: InventarioEntity)

    @Update
    suspend fun updateInventario(entity: InventarioEntity)

    @Insert
    suspend fun insertMovimiento(entity: MovimientoEntity)

    // Articulo (si es nuevo) + upsert de inventario, aplicando el delta con
    // signo via EventoAditivoCombiner en vez de un UPDATE cantidad = X
    // directo (PLAN.md Parte 6, "Decisiones abiertas") + movimiento tipo
    // "entrada" - todo en una sola transaccion Room, mismo patron que
    // VentaDao.insertVentaCompleta (Parte 7) y SucursalDao.insertIfEmpty
    // (Parte 6).
    @Transaction
    suspend fun insertEntradaCompleta(
        articuloNuevo: ArticuloEntity?,
        sucursalId: String,
        articuloId: String,
        deltaCantidad: BigDecimal,
        ubicacion: String?,
        movimiento: MovimientoEntity,
        now: Long,
    ) {
        articuloNuevo?.let { insertArticulo(it) }

        val existente = getInventario(sucursalId, articuloId)
        if (existente == null) {
            insertInventario(
                InventarioEntity(
                    localId = UUID.randomUUID().toString(),
                    remoteId = null,
                    sucursalId = sucursalId,
                    articuloId = articuloId,
                    cantidad = EventoAditivoCombiner.combinar(BigDecimal.ZERO, deltaCantidad, BigDecimal.ZERO),
                    ubicacion = ubicacion,
                    updatedAt = now,
                    isSynced = false,
                    deletedAt = null,
                ),
            )
        } else {
            updateInventario(
                existente.copy(
                    cantidad = EventoAditivoCombiner.combinar(existente.cantidad, deltaCantidad, BigDecimal.ZERO),
                    ubicacion = ubicacion ?: existente.ubicacion,
                    updatedAt = now,
                    isSynced = false,
                ),
            )
        }

        insertMovimiento(movimiento)
    }
}
