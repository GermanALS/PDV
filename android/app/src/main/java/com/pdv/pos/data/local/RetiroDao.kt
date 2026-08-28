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
}
