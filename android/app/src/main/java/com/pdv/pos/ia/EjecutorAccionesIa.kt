package com.pdv.pos.ia

import com.pdv.pos.domain.model.ArticuloNuevo
import com.pdv.pos.domain.model.CorteCaja
import com.pdv.pos.domain.model.Devolucion
import com.pdv.pos.domain.model.DevolucionLinea
import com.pdv.pos.domain.model.Entrada
import com.pdv.pos.domain.model.InventarioItem
import com.pdv.pos.domain.model.RetiroEfectivo
import com.pdv.pos.domain.repository.CajaRepository
import com.pdv.pos.domain.repository.DevolucionRepository
import com.pdv.pos.domain.repository.EntradaRepository
import com.pdv.pos.domain.repository.InventarioRepository
import com.pdv.pos.domain.repository.RetiroEfectivoRepository
import com.pdv.pos.inventario.BuscadorArticuloExistente
import com.pdv.pos.inventario.export.ArchivoExportado
import com.pdv.pos.inventario.export.InventarioExportManager
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import kotlinx.coroutines.flow.first
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

    // archivoParaCompartir solo lo completa "exportar_inventario" (sub-parte
    // D, Parte 18): el llamador (ChatViewModel) lo expone en ChatUiState para
    // que el widget dispare el mismo Intent.ACTION_SEND que InventarioScreen.
    data class Ejecutada(override val mensaje: String, val archivoParaCompartir: ArchivoExportado? = null) : ResultadoAccionIa()
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

@Serializable
private data class ParametrosExportarInventarioDto(
    val filtro: String? = null,
)

@Serializable
private data class ParametrosConsultarStockDto(
    val articulo: String? = null,
    val categoria: String? = null,
)

@Serializable
private data class ParametrosConsultarFaqDto(
    val numero: String,
)

// Instruccion tecnica de formato (PLAN.md Parte 16, sub-paso 5, hallazgo de
// pruebas en el Xiaomi): el prompt de sistema aprobado (Parte 15) describe
// las acciones en prosa pero nunca especifica el JSON exacto que
// RespuestaIaDto/AccionIaDto y los DTOs de parametros de arriba esperan -
// sin esto, cada proveedor improvisaba nombres de campo distintos y
// Json.decodeFromString fallaba con SerializationException ("La IA devolvio
// una respuesta con formato invalido") en cualquier pedido de accion. Se
// concatena al prompt de sistema en tiempo de ejecucion (ChatViewModel), sin
// tocar el texto editable/aprobado por el usuario. Las claves de
// "parametros" de abajo DEBEN coincidir exactamente con los DTOs privados
// de esta misma clase (ParametrosAltaArticuloDto/ParametrosRetiroEfectivoDto/
// ParametrosDevolucionDto) - si cambian ahi, hay que actualizar esto tambien.
val FORMATO_SALIDA_ACCIONES = """
Formato de salida obligatorio (JSON, nada de texto fuera del JSON):
{
  "respuesta_usuario": "texto en español, siempre presente",
  "acciones": []
}

Si no proponés ninguna acción, "acciones" debe ser un array vacío: [].
Cuando proponés una acción o una consulta, cada elemento de "acciones" es
un objeto con exactamente estas 3 claves: "modulo", "tipo", "parametros".
Usá el "tipo" exacto y las claves de "parametros" exactas de una de estas
7 opciones (no inventes ni renombres claves; todo valor numérico va como
texto, ej. "10", no 10):

- tipo "alta_articulo" (modulo "entrada"): "parametros" = {"sku": string,
  "nombre": string, "unidadMedida": string, "cantidad": "10", "precioVenta":
  "50.00", "costo": "30.00" (opcional), "descripcion": string (opcional),
  "categoria": string (opcional), "ubicacion": string (opcional),
  "codigoBarras": string (opcional)}
- tipo "corte_parcial" (modulo "caja"): "parametros" = {} (sin campos)
- tipo "retiro_efectivo" (modulo "caja"): "parametros" = {"monto": "500.00",
  "motivo": string (opcional)}
- tipo "registrar_devolucion" (modulo "devoluciones"): "parametros" =
  {"articuloId": string, "cantidad": "2", "motivo": string (opcional),
  "condicion": string (opcional), "ventaId": string (opcional)}
- tipo "exportar_inventario" (modulo "inventario"): "parametros" =
  {"filtro": string (opcional, categoria o texto de busqueda; vacio =
  catalogo completo)}
- tipo "consultar_stock" (modulo "inventario"): "parametros" =
  {"articulo": string (opcional), "categoria": string (opcional); ninguno
  = total general}
- tipo "consultar_faq" (modulo "ia"): "parametros" = {"numero": "12"} (el
  numero de la pregunta del FAQ que coincide con la consulta del usuario;
  la app responde el texto de esa entrada tal cual)
""".trimIndent()

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
    private val buscadorArticuloExistente: BuscadorArticuloExistente,
    private val inventarioRepository: InventarioRepository,
    private val inventarioExportManager: InventarioExportManager,
    private val appLogger: AppLogger,
    private val json: Json,
    private val faqRepository: FaqRepository,
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
                "exportar_inventario" -> ejecutarExportarInventario(accion, sucursalId)
                "consultar_stock" -> ejecutarConsultarStock(accion, sucursalId)
                "consultar_faq" -> ejecutarConsultarFaq(accion)
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
        "exportar_inventario", "consultar_stock" -> "inventario"
        "consultar_faq" -> "ia"
        else -> null
    }

    private suspend fun ejecutarAltaArticulo(accion: AccionIaDto, sucursalId: String, usuarioId: String): ResultadoAccionIa {
        val p = json.decodeFromJsonElement<ParametrosAltaArticuloDto>(accion.parametros)
        val cantidad = BigDecimal(p.cantidad)
        if (cantidad <= BigDecimal.ZERO) {
            return ResultadoAccionIa.Fallida("La cantidad debe ser mayor a 0 para \"${accion.tipo}\".")
        }

        val existente = buscadorArticuloExistente.buscar(sucursalId, p.nombre, p.sku, p.codigoBarras)
        if (existente != null) {
            entradaRepository.registrarEntrada(
                Entrada.DeArticuloExistente(
                    id = UUID.randomUUID().toString(),
                    sucursalId = sucursalId,
                    usuarioId = usuarioId,
                    fecha = System.currentTimeMillis(),
                    cantidad = cantidad,
                    ubicacion = p.ubicacion,
                    articuloId = existente.id,
                ),
            )
            return ResultadoAccionIa.Ejecutada(
                "Se sumaron ${p.cantidad} ${p.unidadMedida} a \"${existente.nombre}\" (ya existía en el catálogo).",
            )
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

    // "filtro" (exportar_inventario) es ambiguo entre categoria y texto de
    // busqueda a proposito (Parte 18, sub-parte D): se resuelve contra las
    // categorias reales del catalogo en vez de pedirle a la IA que distinga
    // los dos casos, ya que ella no tiene ese listado en el contexto.
    private suspend fun ejecutarExportarInventario(accion: AccionIaDto, sucursalId: String): ResultadoAccionIa {
        val p = json.decodeFromJsonElement<ParametrosExportarInventarioDto>(accion.parametros)
        val filtro = p.filtro?.trim()?.takeIf { it.isNotBlank() }
        val items = itemsFiltrados(sucursalId, articulo = null, categoria = null, textoLibre = filtro)
        if (items.isEmpty()) {
            return ResultadoAccionIa.Ejecutada("No hay artículos que coincidan con el filtro, no se generó ningún archivo.")
        }
        val archivo = inventarioExportManager.exportarCsv(items)
        return ResultadoAccionIa.Ejecutada(
            mensaje = "Exporté ${items.size} artículo(s) a CSV, listo para compartir.",
            archivoParaCompartir = archivo,
        )
    }

    private suspend fun ejecutarConsultarStock(accion: AccionIaDto, sucursalId: String): ResultadoAccionIa {
        val p = json.decodeFromJsonElement<ParametrosConsultarStockDto>(accion.parametros)
        val articulo = p.articulo?.trim()?.takeIf { it.isNotBlank() }
        val categoria = p.categoria?.trim()?.takeIf { it.isNotBlank() }
        val items = itemsFiltrados(sucursalId, articulo = articulo, categoria = categoria, textoLibre = null)
        val total = items.sumOf { it.cantidad }
        val descripcion = when {
            categoria != null && articulo != null -> "\"$articulo\" en la categoría \"$categoria\""
            categoria != null -> "la categoría \"$categoria\""
            articulo != null -> "\"$articulo\""
            else -> "el inventario total"
        }
        return ResultadoAccionIa.Ejecutada("El stock de $descripcion es $total.")
    }

    // Consulta de solo lectura (PLAN.md Parte 20): el modelo elige el
    // numero de la pregunta del FAQ curado desde la lista inyectada al
    // prompt (FaqRepository.instruccionFaq) y la app responde el "answer"
    // de esa entrada tal cual, sin llamada extra al LLM - mismo camino de
    // solo lectura que consultar_stock (se ejecuta al instante, sin tarjeta
    // de confirmacion). Un "numero" no numerico lo captura el catch de
    // NumberFormatException de ejecutar().
    private fun ejecutarConsultarFaq(accion: AccionIaDto): ResultadoAccionIa {
        val p = json.decodeFromJsonElement<ParametrosConsultarFaqDto>(accion.parametros)
        val numero = p.numero.toInt()
        val entry = faqRepository.find(numero)
            ?: return ResultadoAccionIa.Fallida("No encontré la pregunta $numero en el FAQ.")
        return ResultadoAccionIa.Ejecutada(entry.answer)
    }

    // Un solo filtro efectivo por llamada (Parte 18, sub-parte D): "articulo"
    // reusa la busqueda LIKE de InventarioRepository.observarInventario
    // (nombre/sku/codigoBarras); "categoria"/"textoLibre" primero traen el
    // catalogo completo y filtran en memoria por Articulo.categoria, ya que
    // observarInventario no busca por categoria. Si "textoLibre" no matchea
    // ninguna categoria real, se reinterpreta como busqueda de texto.
    private suspend fun itemsFiltrados(
        sucursalId: String,
        articulo: String?,
        categoria: String?,
        textoLibre: String?,
    ): List<InventarioItem> {
        if (articulo != null) return todosLosItems(sucursalId, articulo)
        if (categoria != null) return itemsPorCategoria(sucursalId, categoria)
        if (textoLibre != null) {
            val categorias = inventarioRepository.observarCategorias().first()
            return if (categorias.any { it.equals(textoLibre, ignoreCase = true) }) {
                itemsPorCategoria(sucursalId, textoLibre)
            } else {
                todosLosItems(sucursalId, textoLibre)
            }
        }
        return todosLosItems(sucursalId, "")
    }

    private suspend fun itemsPorCategoria(sucursalId: String, categoria: String): List<InventarioItem> =
        todosLosItems(sucursalId, "").filter { it.articulo.categoria.equals(categoria, ignoreCase = true) }

    // Mismo criterio de paginacion que InventarioViewModel.obtenerTodosLosItemsFiltrados
    // (Parte 9/18): esta clase nunca depende de otro ViewModel, asi que
    // recorre InventarioRepository directamente en vez de reusar ese metodo
    // privado.
    private suspend fun todosLosItems(sucursalId: String, busqueda: String): List<InventarioItem> {
        val tamanioPagina = 100
        val items = mutableListOf<InventarioItem>()
        var pagina = 1
        while (true) {
            val resultado = inventarioRepository.observarInventario(sucursalId, busqueda, pagina, tamanioPagina).first()
            items += resultado.items
            if (resultado.items.isEmpty() || items.size >= resultado.total) break
            pagina++
        }
        return items
    }

    private fun inicioDelDia(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
