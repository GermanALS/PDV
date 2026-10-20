package com.pdv.pos.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.util.UUID

// cantidad es un campo real, no una vista derivada de movimientos - se
// actualiza siempre via EventoAditivoCombiner (PLAN.md Parte 6, "Decisiones
// abiertas"), nunca con un UPDATE cantidad = X directo (EntradaDao).
//
// cantidadNum es un espejo REAL de cantidad para poder usar SUM() / ORDER BY
// / comparaciones numericas en SQL (M-9, PLAN.md Parte 28): cantidad se
// guarda como TEXT (Converters.fromBigDecimal -> toPlainString) y el orden
// lexicografico de TEXT no sirve para agregar. cantidad sigue siendo la
// fuente de verdad exacta; cantidadNum solo alimenta consultas agregadas.
// edicionPendiente (PLAN.md Parte 35) marca que el catalogo del articulo o la
// ubicacion de esta fila se editaron localmente y falta subirlos con PATCH
// /articulos/{id}. Es una senal propia: isSynced no sirve porque toda
// escritura local lo deja en false.
//
// Toda escritura de cantidad DEBE pasar por nuevoInventario(...) /
// InventarioEntity.conCantidad(...) para que las dos columnas no se separen.
@Entity(
    tableName = "inventario",
    indices = [Index(value = ["sucursalId", "articuloId"], unique = true)],
)
data class InventarioEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val sucursalId: String,
    val articuloId: String,
    val cantidad: BigDecimal,
    val cantidadNum: Double,
    val ubicacion: String?,
    val updatedAt: Long,
    val isSynced: Boolean,
    val deletedAt: Long?,
    @ColumnInfo(defaultValue = "0") val edicionPendiente: Boolean = false,
    // Codigo HTTP con que el backend rechazo la ultima edicion de catalogo de
    // esta fila; null = no rechazada (PLAN.md Parte 36).
    val syncRechazo: Int? = null,
)

// Fila nueva de inventario con cantidadNum ya derivada de cantidad.
fun nuevoInventario(
    sucursalId: String,
    articuloId: String,
    cantidad: BigDecimal,
    ubicacion: String?,
    now: Long,
    edicionPendiente: Boolean = false,
): InventarioEntity = InventarioEntity(
    localId = UUID.randomUUID().toString(),
    remoteId = null,
    sucursalId = sucursalId,
    articuloId = articuloId,
    cantidad = cantidad,
    cantidadNum = cantidad.toDouble(),
    ubicacion = ubicacion,
    updatedAt = now,
    isSynced = false,
    deletedAt = null,
    edicionPendiente = edicionPendiente,
)

// Nueva cantidad (y su espejo cantidadNum) sobre una fila existente,
// marcandola como no sincronizada. El llamador ajusta ubicacion aparte si
// hace falta.
fun InventarioEntity.conCantidad(nuevaCantidad: BigDecimal, now: Long): InventarioEntity =
    copy(
        cantidad = nuevaCantidad,
        cantidadNum = nuevaCantidad.toDouble(),
        updatedAt = now,
        isSynced = false,
    )
