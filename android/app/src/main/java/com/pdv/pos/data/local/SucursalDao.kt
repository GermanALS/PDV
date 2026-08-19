package com.pdv.pos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SucursalDao {

    @Query("SELECT * FROM sucursales WHERE deletedAt IS NULL")
    fun observeAll(): Flow<List<SucursalEntity>>

    @Query("SELECT COUNT(*) FROM sucursales")
    suspend fun count(): Int

    @Insert
    suspend fun insert(entity: SucursalEntity)

    // count()+insert() en una sola transaccion: Room serializa las
    // transacciones de escritura, asi que dos colectores concurrentes de
    // observeSucursales() no pueden insertar dos veces la sucursal por
    // defecto (PLAN.md Parte 6, "exactamente una sucursal principal").
    @Transaction
    suspend fun insertIfEmpty(entityProvider: suspend () -> SucursalEntity) {
        if (count() == 0) {
            insert(entityProvider())
        }
    }
}
