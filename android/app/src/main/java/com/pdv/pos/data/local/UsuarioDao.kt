package com.pdv.pos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UsuarioDao {

    @Query("SELECT * FROM usuarios WHERE deletedAt IS NULL ORDER BY nombreCompleto")
    fun observeAll(): Flow<List<UsuarioEntity>>

    @Query("SELECT * FROM usuarios WHERE localId = :usuarioId AND deletedAt IS NULL")
    suspend fun getUsuario(usuarioId: String): UsuarioEntity?

    @Query("SELECT * FROM usuarios WHERE username = :username AND deletedAt IS NULL")
    suspend fun getByUsername(username: String): UsuarioEntity?

    @Query("SELECT COUNT(*) FROM usuarios")
    suspend fun count(): Int

    @Insert
    suspend fun insert(entity: UsuarioEntity)

    @Update
    suspend fun update(entity: UsuarioEntity)

    // Bootstrap de login real (PLAN.md Parte 13): sin un usuario existente,
    // un dispositivo LOCAL que nunca sincronizo con el backend no tiene
    // forma de iniciar sesion (mismo problema, y misma solucion, que
    // RolDao.insertIfEmpty).
    @Transaction
    suspend fun insertIfEmpty(entityProvider: suspend () -> UsuarioEntity) {
        if (count() == 0) {
            insert(entityProvider())
        }
    }
}
