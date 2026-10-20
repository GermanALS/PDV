package com.pdv.pos.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

// Cubre la entrada de mercancia de la Parte 8 (tipo="entrada") ademas de la
// salida que genera una venta (Parte 7, tipo="salida") - docs/schema-pos.json.
@Entity(tableName = "movimientos")
data class MovimientoEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val sucursalId: String,
    val articuloId: String,
    val usuarioId: String,
    val tipo: String,
    val cantidad: BigDecimal,
    val ubicacion: String?,
    val referenciaTipo: String?,
    val referenciaId: String?,
    val fecha: Long,
    val updatedAt: Long,
    val isSynced: Boolean,
    val deletedAt: Long?,
    // Codigo HTTP con que el backend rechazo la fila (0 = desconocido, filas
    // descartadas antes de la Parte 36); null = no rechazada (PLAN.md Parte 36).
    val syncRechazo: Int? = null,
)

// Efecto con signo del movimiento sobre la existencia: la salida (venta) se
// guarda en positivo y resta; entrada y ajuste ya llevan su signo.
fun MovimientoEntity.deltaStock(): BigDecimal = if (tipo == "salida") cantidad.negate() else cantidad
