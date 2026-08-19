package com.pdv.pos.di

import com.pdv.pos.data.local.LocalSucursalRepository
import com.pdv.pos.domain.repository.SucursalRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// Enlaza a la implementacion local; el sub-paso 5 (Wiring, PLAN.md Parte 6)
// resuelve local/remota segun BackendMode.
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindSucursalRepository(impl: LocalSucursalRepository): SucursalRepository
}
