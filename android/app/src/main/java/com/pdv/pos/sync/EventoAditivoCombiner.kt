package com.pdv.pos.sync

import java.math.BigDecimal

// Politica de cantidades de inventario decidida en PLAN.md Parte 6
// ("Decisiones abiertas"): hibrido de deltas con signo, sin comparar
// updated_at. Funcion pura.
object EventoAditivoCombiner {
    fun combinar(base: BigDecimal, deltaLocal: BigDecimal, deltaRemoto: BigDecimal): BigDecimal =
        base + deltaLocal + deltaRemoto

    // Igual que combinar, pero informa si el resultado quedo negativo. La
    // rama "delta negativo -> conflicto" quedo diferida desde la Parte 6 y
    // se implementa en la Parte 32 (D4): el delta se aplica igual (stock
    // negativo transitorio), y EventoAditivoSyncEngine registra el conflicto
    // no auto-resuelto para que un admin lo corrija con un ajuste (Parte 9).
    fun combinarConDeteccion(
        base: BigDecimal,
        deltaLocal: BigDecimal,
        deltaRemoto: BigDecimal,
    ): CombinacionResultado {
        val valor = combinar(base, deltaLocal, deltaRemoto)
        return CombinacionResultado(valor = valor, quedoNegativo = valor.signum() < 0)
    }
}

data class CombinacionResultado(val valor: BigDecimal, val quedoNegativo: Boolean)
