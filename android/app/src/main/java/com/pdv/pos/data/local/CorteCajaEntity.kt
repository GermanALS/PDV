package com.pdv.pos.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "cortes_caja")
data class CorteCajaEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val sucursalId: String,
    val usuarioId: String,
    val tipo: String,
    val fechaInicio: Long,
    val fechaFin: Long,
    val totalVentas: BigDecimal,
    val totalEfectivo: BigDecimal,
    val totalTarjeta: BigDecimal,
    val totalRetiros: BigDecimal,
    val montoEsperado: BigDecimal,
    val montoContado: BigDecimal?,
    val diferencia: BigDecimal?,
    val updatedAt: Long,
    val isSynced: Boolean,
    val deletedAt: Long?,
)
