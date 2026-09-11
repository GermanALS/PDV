package com.pdv.pos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.pdv.pos.sync.EventoAditivoCombiner
import java.math.BigDecimal

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

    // Push diferido (PLAN.md Parte 32, Grupo 2): resuelve el articulo_id
    // remoto de cada linea antes de mandar la venta (ver SyncMappers).
    @Query("SELECT * FROM articulos WHERE localId = :articuloId LIMIT 1")
    suspend fun getArticulo(articuloId: String): ArticuloEntity?

    // Push diferido (PLAN.md Parte 32, Grupo 2): ventas creadas offline.
    @Query("SELECT * FROM ventas WHERE isSynced = 0 AND deletedAt IS NULL")
    suspend fun getVentasPendientes(): List<VentaEntity>

    @Query("SELECT COUNT(*) FROM ventas WHERE isSynced = 0 AND deletedAt IS NULL")
    suspend fun contarVentasPendientes(): Int

    @Query("SELECT * FROM venta_detalle WHERE ventaId = :ventaLocalId AND deletedAt IS NULL")
    suspend fun getDetallesDeVenta(ventaLocalId: String): List<VentaDetalleEntity>

    @Query("UPDATE ventas SET remoteId = :remoteId, isSynced = 1 WHERE localId = :localId")
    suspend fun marcarVentaSincronizada(localId: String, remoteId: String)

    // Descarta una venta que el backend rechazo con un error no recuperable
    // (4xx que no es 401, ej. sucursal_id de un backend distinto al actual -
    // PLAN.md Parte 32, hallazgo de verificacion en dispositivo: sin esto,
    // una fila irrecuperable se reintentaba en cada ciclo para siempre,
    // generando miles de requests identicos). remoteId queda null: nunca
    // llego al backend, a diferencia de marcarVentaSincronizada.
    @Query("UPDATE ventas SET isSynced = 1 WHERE localId = :localId")
    suspend fun marcarVentaDescartada(localId: String)

    @Query("UPDATE venta_detalle SET isSynced = 1 WHERE ventaId = :ventaLocalId")
    suspend fun marcarDetallesVentaSincronizados(ventaLocalId: String)

    @Query("UPDATE movimientos SET isSynced = 1 WHERE referenciaTipo = 'venta' AND referenciaId = :ventaLocalId")
    suspend fun marcarMovimientosVentaSincronizados(ventaLocalId: String)

    // Usado por LocalCajaRepository (PLAN.md Parte 10) para agregar totales
    // de un periodo; VentaDao es dueno de la tabla `ventas`.
    @Query(
        "SELECT * FROM ventas WHERE sucursalId = :sucursalId AND estado = 'completada' AND deletedAt IS NULL " +
            "AND fecha BETWEEN :fechaInicio AND :fechaFin",
    )
    suspend fun getVentasDelPeriodo(sucursalId: String, fechaInicio: Long, fechaFin: Long): List<VentaEntity>

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
                    nuevoInventario(
                        sucursalId = movimiento.sucursalId,
                        articuloId = movimiento.articuloId,
                        cantidad = EventoAditivoCombiner.combinar(BigDecimal.ZERO, deltaCantidad, BigDecimal.ZERO),
                        ubicacion = null,
                        now = now,
                    ),
                )
            } else {
                updateInventario(
                    existente.conCantidad(
                        EventoAditivoCombiner.combinar(existente.cantidad, deltaCantidad, BigDecimal.ZERO),
                        now,
                    ),
                )
            }
        }
    }
}
