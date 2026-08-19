package com.pdv.pos.di

import com.pdv.pos.data.ModeAwareEntradaRepository
import com.pdv.pos.data.ModeAwareSucursalRepository
import com.pdv.pos.data.ModeAwareVentaRepository
import com.pdv.pos.domain.repository.EntradaRepository
import com.pdv.pos.domain.repository.SucursalRepository
import com.pdv.pos.domain.repository.VentaRepository
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

    // Resuelve local/remota segun BackendMode (PLAN.md Parte 7, sub-paso 4).
    @Binds
    abstract fun bindVentaRepository(impl: ModeAwareVentaRepository): VentaRepository

    // Resuelve local/remota segun BackendMode (PLAN.md Parte 8, sub-paso 4).
    @Binds
    abstract fun bindEntradaRepository(impl: ModeAwareEntradaRepository): EntradaRepository
}
