package com.pdv.pos.di

import android.content.Context
import androidx.work.WorkManager
import com.pdv.pos.sync.DefaultSyncOrchestrator
import com.pdv.pos.sync.SyncOrchestrator
import com.pdv.pos.sync.push.CorteCajaPusher
import com.pdv.pos.sync.push.DevolucionPusher
import com.pdv.pos.sync.push.EntityPusher
import com.pdv.pos.sync.push.EntradaPusher
import com.pdv.pos.sync.push.InventarioAjustePusher
import com.pdv.pos.sync.push.RetiroPusher
import com.pdv.pos.sync.push.VentaPusher
import com.pdv.pos.sync.pull.CorteCajaPuller
import com.pdv.pos.sync.pull.EntityPuller
import com.pdv.pos.sync.pull.InventarioPuller
import com.pdv.pos.sync.pull.RetiroPuller
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    @Binds
    abstract fun bindSyncOrchestrator(impl: DefaultSyncOrchestrator): SyncOrchestrator

    companion object {
        @Provides
        @Singleton
        fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
            WorkManager.getInstance(context)

        // Orden del push (PLAN.md Parte 32, Grupo 2): entradas antes que
        // ventas y ajustes para que los articulos creados offline existan en
        // el backend cuando esos pushers los referencien por id remoto. Lo
        // consumen el orquestador (push) y el resumen de pendientes del
        // cambio de modo.
        @Provides
        fun providePushers(
            entrada: EntradaPusher,
            venta: VentaPusher,
            ajuste: InventarioAjustePusher,
            corte: CorteCajaPusher,
            retiro: RetiroPusher,
            devolucion: DevolucionPusher,
        ): List<EntityPusher> = listOf(entrada, venta, ajuste, corte, retiro, devolucion)

        @Provides
        fun providePullers(
            inventario: InventarioPuller,
            corte: CorteCajaPuller,
            retiro: RetiroPuller,
        ): List<EntityPuller> = listOf(inventario, corte, retiro)
    }
}
