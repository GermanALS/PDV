package com.pdv.pos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RetiroDao {
    @Insert
    suspend fun insertRetiro(entity: RetiroEfectivoEntity)

    @Query(
        "SELECT * FROM retiros_efectivo WHERE sucursalId = :sucursalId AND deletedAt IS NULL " +
            "AND fecha BETWEEN :fechaInicio AND :fechaFin",
    )
    suspend fun getRetirosDelPeriodo(sucursalId: String, fechaInicio: Long, fechaFin: Long): List<RetiroEfectivoEntity>

    // Historial reactivo (PLAN.md Parte 18, sub-parte F) - ver comentario de
    // CajaDao.observarCortes, mismo criterio.
    @Query("SELECT * FROM retiros_efectivo WHERE sucursalId = :sucursalId AND deletedAt IS NULL ORDER BY fecha DESC")
    fun observarRetiros(sucursalId: String): Flow<List<RetiroEfectivoEntity>>

    // Push diferido (PLAN.md Parte 32, Grupo 2): retiros creados offline.
    @Query("SELECT * FROM retiros_efectivo WHERE isSynced = 0 AND deletedAt IS NULL")
    suspend fun getRetirosPendientes(): List<RetiroEfectivoEntity>

    @Query("SELECT COUNT(*) FROM retiros_efectivo WHERE isSynced = 0 AND deletedAt IS NULL")
    suspend fun contarRetirosPendientes(): Int

    @Query("UPDATE retiros_efectivo SET remoteId = :remoteId, isSynced = 1 WHERE localId = :localId")
    suspend fun marcarRetiroSincronizado(localId: String, remoteId: String)

    // Descarta un retiro irrecuperable (ver VentaDao.marcarVentaDescartada).
    @Query("UPDATE retiros_efectivo SET isSynced = 1 WHERE localId = :localId")
    suspend fun marcarRetiroDescartado(localId: String)

    // Pull diferido (PLAN.md Parte 32, Grupo 3): insert-if-absent por localId.
    @Query("SELECT * FROM retiros_efectivo WHERE localId = :localId LIMIT 1")
    suspend fun getRetiroByLocalId(localId: String): RetiroEfectivoEntity?
}
