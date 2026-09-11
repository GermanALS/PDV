package com.pdv.pos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.pdv.pos.sync.EventoAditivoCombiner
import java.math.BigDecimal

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

    // Push diferido (PLAN.md Parte 32, Grupo 2): no hay entidad "entrada", la
    // unidad a subir es el movimiento tipo "entrada" (+ el articulo si fue
    // creado en la misma entrada). El local_id que el backend usa como clave
    // de idempotencia es el referenciaId del movimiento (el id de dominio de
    // la entrada), no el localId del movimiento.
    @Query("SELECT * FROM movimientos WHERE tipo = 'entrada' AND isSynced = 0 AND deletedAt IS NULL")
    suspend fun getMovimientosEntradaPendientes(): List<MovimientoEntity>

    @Query("SELECT COUNT(*) FROM movimientos WHERE tipo = 'entrada' AND isSynced = 0 AND deletedAt IS NULL")
    suspend fun contarEntradasPendientes(): Int

    @Query("SELECT * FROM articulos WHERE localId = :localId LIMIT 1")
    suspend fun getArticulo(localId: String): ArticuloEntity?

    @Query("UPDATE movimientos SET remoteId = :remoteId, isSynced = 1 WHERE localId = :localId")
    suspend fun marcarMovimientoSincronizado(localId: String, remoteId: String)

    @Query("UPDATE articulos SET remoteId = :remoteId, isSynced = 1 WHERE localId = :localId")
    suspend fun marcarArticuloSincronizado(localId: String, remoteId: String)

    // Descarta una entrada irrecuperable (ver VentaDao.marcarVentaDescartada).
    @Query("UPDATE movimientos SET isSynced = 1 WHERE localId = :localId")
    suspend fun marcarMovimientoDescartado(localId: String)

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
                nuevoInventario(
                    sucursalId = sucursalId,
                    articuloId = articuloId,
                    cantidad = EventoAditivoCombiner.combinar(BigDecimal.ZERO, deltaCantidad, BigDecimal.ZERO),
                    ubicacion = ubicacion,
                    now = now,
                ),
            )
        } else {
            updateInventario(
                existente
                    .conCantidad(EventoAditivoCombiner.combinar(existente.cantidad, deltaCantidad, BigDecimal.ZERO), now)
                    .copy(ubicacion = ubicacion ?: existente.ubicacion),
            )
        }

        insertMovimiento(movimiento)
    }
}
