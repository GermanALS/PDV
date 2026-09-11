package com.pdv.pos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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

    // Push diferido (PLAN.md Parte 32, Grupo 2): devoluciones creadas offline.
    @Query("SELECT * FROM devoluciones WHERE isSynced = 0 AND deletedAt IS NULL")
    suspend fun getDevolucionesPendientes(): List<DevolucionEntity>

    @Query("SELECT COUNT(*) FROM devoluciones WHERE isSynced = 0 AND deletedAt IS NULL")
    suspend fun contarDevolucionesPendientes(): Int

    @Query("SELECT * FROM devolucion_detalle WHERE devolucionId = :devolucionLocalId AND deletedAt IS NULL")
    suspend fun getDetallesDeDevolucion(devolucionLocalId: String): List<DevolucionDetalleEntity>

    @Query("UPDATE devoluciones SET remoteId = :remoteId, isSynced = 1 WHERE localId = :localId")
    suspend fun marcarDevolucionSincronizada(localId: String, remoteId: String)

    // Descarta una devolucion irrecuperable (ver VentaDao.marcarVentaDescartada).
    @Query("UPDATE devoluciones SET isSynced = 1 WHERE localId = :localId")
    suspend fun marcarDevolucionDescartada(localId: String)

    // Push diferido (PLAN.md Parte 32, Grupo 2): resuelve el articulo_id
    // remoto de cada linea, y el venta_id remoto si la devolucion referencia
    // una venta (ver SyncMappers).
    @Query("SELECT * FROM articulos WHERE localId = :articuloId LIMIT 1")
    suspend fun getArticulo(articuloId: String): ArticuloEntity?

    @Query("SELECT remoteId FROM ventas WHERE localId = :ventaLocalId LIMIT 1")
    suspend fun getVentaRemoteId(ventaLocalId: String): String?

    @Query("UPDATE devolucion_detalle SET isSynced = 1 WHERE devolucionId = :devolucionLocalId")
    suspend fun marcarDetallesDevolucionSincronizados(devolucionLocalId: String)
}
