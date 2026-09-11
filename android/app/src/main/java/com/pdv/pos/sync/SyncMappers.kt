package com.pdv.pos.sync

import com.pdv.pos.data.local.ArticuloEntity
import com.pdv.pos.data.local.CorteCajaEntity
import com.pdv.pos.data.local.DevolucionDetalleEntity
import com.pdv.pos.data.local.DevolucionEntity
import com.pdv.pos.data.local.MovimientoEntity
import com.pdv.pos.data.local.RetiroEfectivoEntity
import com.pdv.pos.data.local.VentaDetalleEntity
import com.pdv.pos.data.local.VentaEntity
import com.pdv.pos.data.remote.dto.ArticuloNuevoRequestDto
import com.pdv.pos.data.remote.dto.CorteCajaCreateRequestDto
import com.pdv.pos.data.remote.dto.CorteCajaDto
import com.pdv.pos.data.remote.dto.DevolucionCreateRequestDto
import com.pdv.pos.data.remote.dto.DevolucionDetalleCreateRequestDto
import com.pdv.pos.data.remote.dto.EntradaCreateRequestDto
import com.pdv.pos.data.remote.dto.RetiroEfectivoCreateRequestDto
import com.pdv.pos.data.remote.dto.RetiroEfectivoDto
import com.pdv.pos.data.remote.dto.VentaCreateRequestDto
import com.pdv.pos.data.remote.dto.VentaDetalleCreateRequestDto
import java.math.BigDecimal
import java.time.Instant

// Entity -> *CreateRequestDto directo (PLAN.md Parte 32, D3.1): el push del
// motor de sync diferido no pasa por el modelo de dominio. El local_id que
// viaja es el que el backend usa como clave de idempotencia
// (UNIQUE(local_id), Parte 23). Fechas Long epoch-millis -> ISO-8601; montos
// BigDecimal -> String con toPlainString (misma convencion que los
// Remote*Repository).

// articuloIdRemoto resuelve el articulo_id LOCAL de cada linea a su id
// remoto: el backend no conoce los ids locales de Room, solo los suyos
// propios (PLAN.md Parte 32, hallazgo de verificacion en dispositivo -
// enviar el id local producia un 404 "articulo no encontrado" permanente
// en toda venta con lineas, no solo en datos historicos). El llamador
// (VentaPusher) debe resolverlo antes de mapear; si algun articulo de la
// venta todavia no tiene remoteId, la venta se aplaza, no se manda.
internal fun VentaEntity.toCreateRequestDto(
    detalles: List<VentaDetalleEntity>,
    articuloIdRemoto: (String) -> String,
) = VentaCreateRequestDto(
    localId = localId,
    sucursalId = sucursalId,
    usuarioId = usuarioId,
    folio = folio,
    fecha = Instant.ofEpochMilli(fecha).toString(),
    subtotal = subtotal.toPlainString(),
    descuento = descuento.toPlainString(),
    impuestos = impuestos.toPlainString(),
    total = total.toPlainString(),
    metodoPago = metodoPago,
    estado = estado,
    lineas = detalles.map { it.toCreateRequestDto(articuloIdRemoto) },
)

private fun VentaDetalleEntity.toCreateRequestDto(articuloIdRemoto: (String) -> String) = VentaDetalleCreateRequestDto(
    localId = localId,
    articuloId = articuloIdRemoto(articuloId),
    cantidad = cantidad.toPlainString(),
    precioUnitario = precioUnitario.toPlainString(),
    subtotal = subtotal.toPlainString(),
)

// El local_id de la entrada es el referenciaId del movimiento (id de dominio
// de la entrada), no el localId del movimiento. articuloNuevo != null cuando
// el articulo se creo en esta misma entrada y todavia no esta en el backend;
// si no, se manda articuloIdRemoto (el id del backend, no el local).
internal fun MovimientoEntity.toEntradaCreateRequestDto(
    articuloIdRemoto: String?,
    articuloNuevo: ArticuloEntity?,
) = EntradaCreateRequestDto(
    localId = referenciaId ?: localId,
    sucursalId = sucursalId,
    usuarioId = usuarioId,
    fecha = Instant.ofEpochMilli(fecha).toString(),
    cantidad = cantidad.toPlainString(),
    ubicacion = ubicacion,
    articuloId = if (articuloNuevo == null) (articuloIdRemoto ?: articuloId) else null,
    articuloNuevo = articuloNuevo?.toRequestDto(),
)

private fun ArticuloEntity.toRequestDto() = ArticuloNuevoRequestDto(
    localId = localId,
    codigoBarras = codigoBarras,
    sku = sku,
    nombre = nombre,
    descripcion = descripcion,
    categoria = categoria,
    unidadMedida = unidadMedida,
    precioVenta = precioVenta.toPlainString(),
    costo = costo?.toPlainString(),
)

internal fun CorteCajaEntity.toCreateRequestDto() = CorteCajaCreateRequestDto(
    localId = localId,
    sucursalId = sucursalId,
    usuarioId = usuarioId,
    tipo = tipo,
    fechaInicio = Instant.ofEpochMilli(fechaInicio).toString(),
    fechaFin = Instant.ofEpochMilli(fechaFin).toString(),
    totalVentas = totalVentas.toPlainString(),
    totalEfectivo = totalEfectivo.toPlainString(),
    totalTarjeta = totalTarjeta.toPlainString(),
    totalRetiros = totalRetiros.toPlainString(),
    montoEsperado = montoEsperado.toPlainString(),
    montoContado = montoContado?.toPlainString(),
    diferencia = diferencia?.toPlainString(),
)

internal fun RetiroEfectivoEntity.toCreateRequestDto() = RetiroEfectivoCreateRequestDto(
    localId = localId,
    sucursalId = sucursalId,
    usuarioId = usuarioId,
    monto = monto.toPlainString(),
    motivo = motivo,
    fecha = Instant.ofEpochMilli(fecha).toString(),
)

// ventaIdRemoto y articuloIdRemoto: mismo motivo que en VentaEntity de mas
// arriba. ventaIdRemoto ya resuelto por el llamador (null si la devolucion
// no referencia ninguna venta; si la referenciaba pero esa venta aun no
// sincronizo, la devolucion se aplaza en vez de mandar un venta_id local).
internal fun DevolucionEntity.toCreateRequestDto(
    detalles: List<DevolucionDetalleEntity>,
    ventaIdRemoto: String?,
    articuloIdRemoto: (String) -> String,
) = DevolucionCreateRequestDto(
    localId = localId,
    sucursalId = sucursalId,
    usuarioId = usuarioId,
    ventaId = ventaIdRemoto,
    folio = folio,
    fecha = Instant.ofEpochMilli(fecha).toString(),
    estado = estado,
    lineas = detalles.map { it.toCreateRequestDto(articuloIdRemoto) },
)

private fun DevolucionDetalleEntity.toCreateRequestDto(articuloIdRemoto: (String) -> String) = DevolucionDetalleCreateRequestDto(
    localId = localId,
    articuloId = articuloIdRemoto(articuloId),
    cantidad = cantidad.toPlainString(),
    motivo = motivo,
    condicion = condicion,
)

// --- Pull (PLAN.md Parte 32, Grupo 3): DTO -> Entity para el merge a Room de
// cortes/retiros creados en otras terminales de la misma sucursal. Filas
// inmutables, se insertan con isSynced = true.

internal fun CorteCajaDto.toEntity(localIdCorrelacionado: String) = CorteCajaEntity(
    localId = localIdCorrelacionado,
    remoteId = id,
    sucursalId = sucursalId,
    usuarioId = usuarioId,
    tipo = tipo,
    fechaInicio = Instant.parse(fechaInicio).toEpochMilli(),
    fechaFin = Instant.parse(fechaFin).toEpochMilli(),
    totalVentas = BigDecimal(totalVentas),
    totalEfectivo = BigDecimal(totalEfectivo),
    totalTarjeta = BigDecimal(totalTarjeta),
    totalRetiros = BigDecimal(totalRetiros),
    montoEsperado = BigDecimal(montoEsperado),
    montoContado = montoContado?.let { BigDecimal(it) },
    diferencia = diferencia?.let { BigDecimal(it) },
    updatedAt = Instant.parse(updatedAt).toEpochMilli(),
    isSynced = true,
    deletedAt = deletedAt?.let { Instant.parse(it).toEpochMilli() },
)

internal fun RetiroEfectivoDto.toEntity(localIdCorrelacionado: String) = RetiroEfectivoEntity(
    localId = localIdCorrelacionado,
    remoteId = id,
    sucursalId = sucursalId,
    usuarioId = usuarioId,
    monto = BigDecimal(monto),
    motivo = motivo,
    fecha = Instant.parse(fecha).toEpochMilli(),
    updatedAt = Instant.parse(updatedAt).toEpochMilli(),
    isSynced = true,
    deletedAt = deletedAt?.let { Instant.parse(it).toEpochMilli() },
)
