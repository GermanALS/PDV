package com.pdv.pos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UsuarioDao {

    @Query("SELECT * FROM usuarios WHERE deletedAt IS NULL ORDER BY nombreCompleto")
    fun observeAll(): Flow<List<UsuarioEntity>>

    @Query("SELECT * FROM usuarios WHERE localId = :usuarioId AND deletedAt IS NULL")
    suspend fun getUsuario(usuarioId: String): UsuarioEntity?

    @Insert
    suspend fun insert(entity: UsuarioEntity)

    @Update
    suspend fun update(entity: UsuarioEntity)
}
