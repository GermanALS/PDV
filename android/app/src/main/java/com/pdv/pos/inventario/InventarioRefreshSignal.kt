package com.pdv.pos.inventario

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

// Fuerza un refetch de InventarioViewModel tras una escritura externa a
// inventario/articulos (Entrada, Venta, alta_articulo de la IA) - en modo
// REMOTO, RemoteInventarioRepository.observarInventario es un flow{} de un
// solo disparo (PLAN.md Parte 18, sub-parte E), asi que sin esta senial la
// pantalla de Inventario ya abierta no se entera de escrituras hechas desde
// otras pantallas. No-op en modo LOCAL, donde el Flow de Room ya se
// refresca solo - emitir igual ahi es inofensivo (solo un flatMapLatest
// extra sobre datos ya frescos).
@Singleton
class InventarioRefreshSignal @Inject constructor() {

    // replay=1 (no extraBufferCapacity) para que un colector que se
    // suscribe DESPUES de un emitir() (ej. abrir Inventario luego de
    // guardar una Entrada) igual reciba ese ultimo refresco - con solo
    // extraBufferCapacity, el valor se pierde si no habia un colector activo
    // en el momento del emit (verificado: asi fallaban las pruebas antes de
    // este cambio).
    private val _refrescos = MutableSharedFlow<Unit>(replay = 1)
    val refrescos: SharedFlow<Unit> = _refrescos.asSharedFlow()

    fun emitir() {
        _refrescos.tryEmit(Unit)
    }
}
