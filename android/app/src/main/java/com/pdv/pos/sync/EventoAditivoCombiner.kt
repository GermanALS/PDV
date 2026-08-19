package com.pdv.pos.sync

import java.math.BigDecimal

// Politica de cantidades de inventario decidida en PLAN.md Parte 6
// ("Decisiones abiertas"): hibrido de deltas con signo, sin comparar
// updated_at. Funcion pura, sin entidad real todavia (se valida con
// inventario real en la Parte 7/9).
object EventoAditivoCombiner {
    fun combinar(base: BigDecimal, deltaLocal: BigDecimal, deltaRemoto: BigDecimal): BigDecimal =
        base + deltaLocal + deltaRemoto
}
