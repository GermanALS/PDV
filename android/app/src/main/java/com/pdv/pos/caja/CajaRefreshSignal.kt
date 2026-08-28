package com.pdv.pos.caja

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

// Fuerza un refetch del historial de Caja tras un corte/retiro guardado
// desde fuera de CajaViewModel (ej. la acciones corte_parcial/retiro_efectivo
// de la IA) - mismo problema y misma solucion que InventarioRefreshSignal
// (PLAN.md Parte 18, sub-partes E y F): en modo REMOTO,
// RemoteCajaRepository/RemoteRetiroEfectivoRepository.observe* son un
// flow{} de un solo disparo, asi que sin esta senial la pantalla de Caja ya
// abierta no se entera de escrituras hechas desde otro lado. No-op en modo
// LOCAL, donde el Flow de Room ya se refresca solo. replay = 1 (no solo
// extraBufferCapacity): un colector que se suscribe despues del emit igual
// debe recibirlo (ver InventarioRefreshSignal para el hallazgo de pruebas
// que lo confirmo).
@Singleton
class CajaRefreshSignal @Inject constructor() {

    private val _refrescos = MutableSharedFlow<Unit>(replay = 1)
    val refrescos: SharedFlow<Unit> = _refrescos.asSharedFlow()

    fun emitir() {
        _refrescos.tryEmit(Unit)
    }
}
