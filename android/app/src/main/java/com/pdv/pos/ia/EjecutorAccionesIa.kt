package com.pdv.pos.ia

import com.pdv.pos.domain.model.ArticuloNuevo
import com.pdv.pos.domain.model.CorteCaja
import com.pdv.pos.domain.model.Devolucion
import com.pdv.pos.domain.model.DevolucionLinea
import com.pdv.pos.domain.model.Entrada
import com.pdv.pos.domain.model.RetiroEfectivo
import com.pdv.pos.domain.repository.CajaRepository
import com.pdv.pos.domain.repository.DevolucionRepository
import com.pdv.pos.domain.repository.EntradaRepository
import com.pdv.pos.domain.repository.RetiroEfectivoRepository
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import retrofit2.HttpException
import java.io.IOException
import java.math.BigDecimal
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

// Resultado de una accion individual (PLAN.md Parte 15, sub-paso 3,
// Decision 2: independiente por accion - no hay estado compartido entre
// acciones de una misma respuesta). "mensaje" es lo que se informa en el
// chat en los 3 casos, incluida la Rechazada (AUTH) y la Fallida (fallo
// tecnico al ejecutar el caso de uso real).
sealed class ResultadoAccionIa {
    abstract val mensaje: String

    data class Ejecutada(override val mensaje: String) : ResultadoAccionIa()
    data class RechazadaPorPermiso(override val mensaje: String) : ResultadoAccionIa()
    data class Fallida(override val mensaje: String) : ResultadoAccionIa()
}

@Serializable
private data class ParametrosAltaArticuloDto(
    val sku: String,
    val nombre: String,
    val unidadMedida: String,
    val cantidad: String,
    val precioVenta: String,
    val costo: String? = null,
    val descripcion: String? = null,
    val categoria: String? = null,
    val ubicacion: String? = null,
    val codigoBarras: String? = null,
)

@Serializable
private data class ParametrosRetiroEfectivoDto(
    val monto: String,
    val motivo: String? = null,
)

@Serializable
private data class ParametrosDevolucionDto(
    val articuloId: String,
    val cantidad: String,
    val motivo: String? = null,
    val condicion: String? = null,
    val ventaId: String? = null,
)

// Valida permisos y ejecuta las acciones que la IA propone (PLAN.md
// Parte 15, sub-paso 3): cada tipo de accion llama exactamente al mismo
// repositorio real que su pantalla manual (EntradaRepository,
// CajaRepository/RetiroEfectivoRepository, DevolucionRepository) - sin
// camino de escritura aparte. La confirmacion explicita del usuario antes
// de llamar a ejecutar() es responsabilidad del llamador (Parte 16, UI del
// chat); esta clase asume que la accion ya fue confirmada y solo falta el
// chequeo de permiso.
class EjecutorAccionesIa @Inject constructor(
    private val entradaRepository: EntradaRepository,
    private val cajaRepository: CajaRepository,
    private val retiroEfectivoRepository: RetiroEfectivoRepository,
    private val devolucionRepository: DevolucionRepository,
    private val appLogger: AppLogger,
    private val json: Json,
) {
    suspend fun ejecutar(
        accion: AccionIaDto,
        modulosPermitidos: Set<String>,
        sucursalId: String,
        usuarioId: String,
    ): ResultadoAccionIa {
        // El modulo que autoriza SIEMPRE se deriva de accion.tipo (mapeo fijo
        // en codigo), nunca de accion.modulo - los dos campos vienen de la
        // misma fuente no confiable (la salida del LLM) y nada los mantiene
        // consistentes entre si; confiar en accion.modulo para autorizar
        // permitiria que una respuesta con modulo/tipo cruzados (ej. modulo
        // "entrada" pero tipo "corte_parcial") saltee el permiso real de
        // "caja" (hallazgo de code-reviewer).
        val moduloRequerido = moduloRequeridoPorTipo(accion.tipo)
            ?: return ResultadoAccionIa.Fallida("La IA propuso un tipo de acción no reconocido: \"${accion.tipo}\".")

        if (moduloRequerido !in modulosPermitidos) {
            appLogger.log(
                LogType.AUTH,
                sucursalId = sucursalId,
                usuario = usuarioId,
                mensaje = "IA: accion rechazada por falta de permiso en el modulo '$moduloRequerido' (tipo: ${accion.tipo})",
            )
            return ResultadoAccionIa.RechazadaPorPermiso(
                "No tenés permiso para el módulo \"$moduloRequerido\", así que no realicé esta acción.",
            )
        }

        return try {
            when (accion.tipo) {
                "alta_articulo" -> ejecutarAltaArticulo(accion, sucursalId, usuarioId)
                "corte_parcial" -> ejecutarCorteParcial(sucursalId, usuarioId)
                "retiro_efectivo" -> ejecutarRetiroEfectivo(accion, sucursalId, usuarioId)
                "registrar_devolucion" -> ejecutarDevolucion(accion, sucursalId, usuarioId)
                else -> error("tipo ya validado por moduloRequeridoPorTipo: ${accion.tipo}")
            }
        } catch (e: SerializationException) {
            ResultadoAccionIa.Fallida("La IA propuso parámetros inválidos para \"${accion.tipo}\".")
        } catch (e: NumberFormatException) {
            ResultadoAccionIa.Fallida("La IA propuso un valor numérico inválido para \"${accion.tipo}\".")
        } catch (e: IOException) {
            ResultadoAccionIa.Fallida("No se pudo completar \"${accion.tipo}\": sin conexión.")
        } catch (e: HttpException) {
            ResultadoAccionIa.Fallida("No se pudo completar \"${accion.tipo}\": error del servidor.")
        }
    }

    private fun moduloRequeridoPorTipo(tipo: String): String? = when (tipo) {
        "alta_articulo" -> "entrada"
        "corte_parcial", "retiro_efectivo" -> "caja"
        "registrar_devolucion" -> "devoluciones"
        else -> null
    }

    private suspend fun ejecutarAltaArticulo(accion: AccionIaDto, sucursalId: String, usuarioId: String): ResultadoAccionIa {
        val p = json.decodeFromJsonElement<ParametrosAltaArticuloDto>(accion.parametros)
        val cantidad = BigDecimal(p.cantidad)
        if (cantidad <= BigDecimal.ZERO) {
            return ResultadoAccionIa.Fallida("La cantidad debe ser mayor a 0 para \"${accion.tipo}\".")
        }
        val entrada = Entrada.DeArticuloNuevo(
            id = UUID.randomUUID().toString(),
            sucursalId = sucursalId,
            usuarioId = usuarioId,
            fecha = System.currentTimeMillis(),
            cantidad = cantidad,
            ubicacion = p.ubicacion,
            articulo = ArticuloNuevo(
                id = UUID.randomUUID().toString(),
                codigoBarras = p.codigoBarras,
                sku = p.sku,
                nombre = p.nombre,
                descripcion = p.descripcion,
                categoria = p.categoria,
                unidadMedida = p.unidadMedida,
                precioVenta = BigDecimal(p.precioVenta),
                costo = p.costo?.let(::BigDecimal),
            ),
        )
        entradaRepository.registrarEntrada(entrada)
        return ResultadoAccionIa.Ejecutada("Alta registrada: ${p.nombre} (${p.cantidad} ${p.unidadMedida}).")
    }

    private suspend fun ejecutarCorteParcial(sucursalId: String, usuarioId: String): ResultadoAccionIa {
        val ahora = System.currentTimeMillis()
        val inicio = inicioDelDia()
        val totales = cajaRepository.calcularTotales(sucursalId, inicio, ahora)
        val corte = CorteCaja(
            id = UUID.randomUUID().toString(),
            sucursalId = sucursalId,
            usuarioId = usuarioId,
            tipo = "parcial",
            fechaInicio = inicio,
            fechaFin = ahora,
            totalVentas = totales.totalVentas,
            totalEfectivo = totales.totalEfectivo,
            totalTarjeta = totales.totalTarjeta,
            totalRetiros = totales.totalRetiros,
            montoEsperado = totales.montoEsperado,
            montoContado = null,
            diferencia = null,
        )
        cajaRepository.guardarCorte(corte)
        return ResultadoAccionIa.Ejecutada("Corte parcial registrado. Monto esperado: ${totales.montoEsperado}.")
    }

    private suspend fun ejecutarRetiroEfectivo(accion: AccionIaDto, sucursalId: String, usuarioId: String): ResultadoAccionIa {
        val p = json.decodeFromJsonElement<ParametrosRetiroEfectivoDto>(accion.parametros)
        val monto = BigDecimal(p.monto)
        if (monto <= BigDecimal.ZERO) {
            return ResultadoAccionIa.Fallida("El monto debe ser mayor a 0 para \"${accion.tipo}\".")
        }
        val retiro = RetiroEfectivo(
            id = UUID.randomUUID().toString(),
            sucursalId = sucursalId,
            usuarioId = usuarioId,
            monto = monto,
            motivo = p.motivo,
            fecha = System.currentTimeMillis(),
        )
        retiroEfectivoRepository.registrarRetiro(retiro)
        return ResultadoAccionIa.Ejecutada("Retiro de $monto registrado.")
    }

    private suspend fun ejecutarDevolucion(accion: AccionIaDto, sucursalId: String, usuarioId: String): ResultadoAccionIa {
        val p = json.decodeFromJsonElement<ParametrosDevolucionDto>(accion.parametros)
        val cantidad = BigDecimal(p.cantidad)
        if (cantidad <= BigDecimal.ZERO) {
            return ResultadoAccionIa.Fallida("La cantidad debe ser mayor a 0 para \"${accion.tipo}\".")
        }
        val ahora = System.currentTimeMillis()
        val devolucion = Devolucion(
            id = UUID.randomUUID().toString(),
            sucursalId = sucursalId,
            usuarioId = usuarioId,
            ventaId = p.ventaId,
            folio = "D-$ahora",
            fecha = ahora,
            estado = "registrada",
            lineas = listOf(
                DevolucionLinea(
                    articuloId = p.articuloId,
                    cantidad = cantidad,
                    motivo = p.motivo,
                    condicion = p.condicion,
                ),
            ),
        )
        devolucionRepository.registrarDevolucion(devolucion)
        return ResultadoAccionIa.Ejecutada("Devolución registrada: folio ${devolucion.folio}.")
    }

    private fun inicioDelDia(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
