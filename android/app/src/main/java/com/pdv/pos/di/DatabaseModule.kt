package com.pdv.pos.di

import android.content.Context
import androidx.room.Room
import com.pdv.pos.data.local.PdvDatabase
import com.pdv.pos.data.local.SucursalDao
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
        Room.databaseBuilder(context, PdvDatabase::class.java, "pdv.db").build()

    @Provides
    fun provideSucursalDao(database: PdvDatabase): SucursalDao = database.sucursalDao()
}
