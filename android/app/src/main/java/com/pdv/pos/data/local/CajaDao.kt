package com.pdv.pos.data.local

import androidx.room.Dao
import androidx.room.Insert

@Dao
interface CajaDao {
    @Insert
    suspend fun insertCorte(entity: CorteCajaEntity)
}
