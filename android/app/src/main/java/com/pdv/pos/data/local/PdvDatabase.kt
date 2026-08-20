package com.pdv.pos.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        SucursalEntity::class,
        SyncConflictEntity::class,
        VentaEntity::class,
        VentaDetalleEntity::class,
        MovimientoEntity::class,
        ArticuloEntity::class,
        InventarioEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class PdvDatabase : RoomDatabase() {
    abstract fun sucursalDao(): SucursalDao
    abstract fun syncConflictDao(): SyncConflictDao
    abstract fun ventaDao(): VentaDao
    abstract fun entradaDao(): EntradaDao
    abstract fun inventarioDao(): InventarioDao
}
