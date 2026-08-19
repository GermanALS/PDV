package com.pdv.pos.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SucursalEntity::class, SyncConflictEntity::class], version = 1, exportSchema = false)
abstract class PdvDatabase : RoomDatabase() {
    abstract fun sucursalDao(): SucursalDao
    abstract fun syncConflictDao(): SyncConflictDao
}
