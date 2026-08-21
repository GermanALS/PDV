package com.pdv.pos.di

import android.content.Context
import androidx.room.Room
import com.pdv.pos.data.local.CajaDao
import com.pdv.pos.data.local.DevolucionDao
import com.pdv.pos.data.local.EntradaDao
import com.pdv.pos.data.local.InventarioDao
import com.pdv.pos.data.local.PdvDatabase
import com.pdv.pos.data.local.RetiroDao
import com.pdv.pos.data.local.SucursalDao
import com.pdv.pos.data.local.SyncConflictDao
import com.pdv.pos.data.local.UsuarioDao
import com.pdv.pos.data.local.VentaDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providePdvDatabase(@ApplicationContext context: Context): PdvDatabase =
        Room.databaseBuilder(context, PdvDatabase::class.java, "pdv.db")
            // Sin migraciones formales todavia (proyecto en desarrollo activo,
            // sin datos de produccion que preservar) - un cambio de version
            // recrea el esquema en vez de crashear en el siguiente arranque.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideSucursalDao(database: PdvDatabase): SucursalDao = database.sucursalDao()

    @Provides
    fun provideSyncConflictDao(database: PdvDatabase): SyncConflictDao = database.syncConflictDao()

    @Provides
    fun provideVentaDao(database: PdvDatabase): VentaDao = database.ventaDao()

    @Provides
    fun provideEntradaDao(database: PdvDatabase): EntradaDao = database.entradaDao()

    @Provides
    fun provideInventarioDao(database: PdvDatabase): InventarioDao = database.inventarioDao()

    @Provides
    fun provideCajaDao(database: PdvDatabase): CajaDao = database.cajaDao()

    @Provides
    fun provideRetiroDao(database: PdvDatabase): RetiroDao = database.retiroDao()

    @Provides
    fun provideDevolucionDao(database: PdvDatabase): DevolucionDao = database.devolucionDao()

    @Provides
    fun provideUsuarioDao(database: PdvDatabase): UsuarioDao = database.usuarioDao()
}
