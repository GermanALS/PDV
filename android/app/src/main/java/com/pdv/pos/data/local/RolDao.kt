package com.pdv.pos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RolDao {

    @Query("SELECT * FROM roles WHERE deletedAt IS NULL ORDER BY nombre")
    fun observeAll(): Flow<List<RolEntity>>

    @Query("SELECT * FROM roles WHERE localId = :rolId AND deletedAt IS NULL")
    suspend fun getRol(rolId: String): RolEntity?

    @Query("SELECT * FROM roles WHERE nombre = :nombre AND deletedAt IS NULL")
    suspend fun getByNombre(nombre: String): RolEntity?

    @Query("SELECT COUNT(*) FROM roles")
    suspend fun count(): Int

    @Insert
    suspend fun insert(entity: RolEntity)

    @Insert
    suspend fun insertAll(entities: List<RolEntity>)

    @Update
    suspend fun update(entity: RolEntity)

    // count()+insertAll() en una sola transaccion, mismo criterio que
    // SucursalDao.insertIfEmpty (PLAN.md Parte 3, "Sucursal por defecto"):
    // sin esto, un dispositivo en modo LOCAL que nunca sincronizo con el
    // backend arranca con `roles` vacia y el modulo Usuarios queda
    // inutilizable (no hay forma de seleccionar un rol al crear un
    // usuario).
    @Transaction
    suspend fun insertIfEmpty(entitiesProvider: suspend () -> List<RolEntity>) {
        if (count() == 0) {
            insertAll(entitiesProvider())
        }
    }
}
