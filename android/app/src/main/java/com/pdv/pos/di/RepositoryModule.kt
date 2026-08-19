package com.pdv.pos.di

import com.pdv.pos.data.ModeAwareSucursalRepository
import com.pdv.pos.domain.repository.SucursalRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    // Resuelve local/remota segun BackendMode (PLAN.md Parte 6, sub-paso 5).
    @Binds
    abstract fun bindSucursalRepository(impl: ModeAwareSucursalRepository): SucursalRepository
}
