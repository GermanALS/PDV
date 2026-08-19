package com.pdv.pos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SyncConflictDao {

    @Insert
    suspend fun insert(entity: SyncConflictEntity)

    @Query("SELECT * FROM sync_conflicts")
    suspend fun getAll(): List<SyncConflictEntity>
}
