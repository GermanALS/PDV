package com.pdv.pos.di

import com.pdv.pos.data.ModeAwareCajaRepository
import com.pdv.pos.data.ModeAwareDevolucionRepository
import com.pdv.pos.data.ModeAwareEntradaRepository
import com.pdv.pos.data.ModeAwareInventarioRepository
import com.pdv.pos.data.ModeAwareRetiroEfectivoRepository
import com.pdv.pos.data.ModeAwareSucursalRepository
import com.pdv.pos.data.ModeAwareUsuarioRepository
import com.pdv.pos.data.ModeAwareVentaRepository
import com.pdv.pos.domain.repository.CajaRepository
import com.pdv.pos.domain.repository.DevolucionRepository
import com.pdv.pos.domain.repository.EntradaRepository
import com.pdv.pos.domain.repository.InventarioRepository
import com.pdv.pos.domain.repository.RetiroEfectivoRepository
import com.pdv.pos.domain.repository.SucursalRepository
import com.pdv.pos.domain.repository.UsuarioRepository
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

    // Resuelve local/remota segun BackendMode (PLAN.md Parte 9, sub-paso 4).
    @Binds
    abstract fun bindInventarioRepository(impl: ModeAwareInventarioRepository): InventarioRepository

    // Resuelve local/remota segun BackendMode (PLAN.md Parte 10, sub-paso 4).
    @Binds
    abstract fun bindCajaRepository(impl: ModeAwareCajaRepository): CajaRepository

    // Resuelve local/remota segun BackendMode (PLAN.md Parte 10, sub-paso 4).
    @Binds
    abstract fun bindRetiroEfectivoRepository(impl: ModeAwareRetiroEfectivoRepository): RetiroEfectivoRepository

    // Resuelve local/remota segun BackendMode (PLAN.md Parte 11, sub-paso 4).
    @Binds
    abstract fun bindDevolucionRepository(impl: ModeAwareDevolucionRepository): DevolucionRepository

    // Resuelve local/remota segun BackendMode (PLAN.md Parte 12, sub-paso 4).
    @Binds
    abstract fun bindUsuarioRepository(impl: ModeAwareUsuarioRepository): UsuarioRepository
}
