package com.pdv.pos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface RetiroDao {
    @Insert
    suspend fun insertRetiro(entity: RetiroEfectivoEntity)

    @Query(
        "SELECT * FROM retiros_efectivo WHERE sucursalId = :sucursalId AND deletedAt IS NULL " +
            "AND fecha BETWEEN :fechaInicio AND :fechaFin",
    )
    suspend fun getRetirosDelPeriodo(sucursalId: String, fechaInicio: Long, fechaFin: Long): List<RetiroEfectivoEntity>
}
