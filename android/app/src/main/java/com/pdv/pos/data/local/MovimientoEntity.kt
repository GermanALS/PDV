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
)
