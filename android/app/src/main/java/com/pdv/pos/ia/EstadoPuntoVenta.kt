package com.pdv.pos.ia

import com.pdv.pos.data.remote.dto.ChatMessageDto
import com.pdv.pos.domain.repository.CajaRepository
import com.pdv.pos.domain.repository.InventarioRepository
import com.pdv.pos.domain.repository.SucursalRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Calendar
import javax.inject.Inject

@Serializable
data class ArticuloResumenDto(
    val sku: String,
    val nombre: String,
    val cantidad: String,
    val precioVenta: String,
    val costo: String?,
    val ubicacion: String?,
)

@Serializable
data class CajaResumenDto(
    val totalVentas: String,
    val totalEfectivo: String,
    val totalTarjeta: String,
    val totalRetiros: String,
    val montoEsperado: String,
)

@Serializable
data class EstadoPuntoVenta(
    val sucursalId: String,
    val sucursalNombre: String?,
    val caja: CajaResumenDto,
    val inventario: List<ArticuloResumenDto>,
    val historial: List<ChatMessageDto>,
)

// Tope del resumen de inventario incluido en el contexto (PLAN.md Parte 15,
// sub-paso 1) - un catalogo completo puede tener miles de articulos, y este
// JSON viaja en cada turno de conversacion como parte del mensaje al LLM.
private const val TAMANIO_RESUMEN_INVENTARIO = 50

// Arma el JSON de contexto que se envia al LLM en cada turno: catalogo/
// inventario resumido, sucursal y caja actual, historial corto de
// conversacion (PLAN.md Parte 15, sub-paso 1). Reutiliza las lecturas ya
// existentes de cada ModeAwareXRepository - no agrega ningun camino de
// escritura ni logica de negocio nueva.
class EstadoPuntoVentaBuilder @Inject constructor(
    private val sucursalRepository: SucursalRepository,
    private val inventarioRepository: InventarioRepository,
    private val cajaRepository: CajaRepository,
    private val json: Json,
) {
    suspend fun armarJson(sucursalId: String, historial: List<ChatMessageDto>): String {
        val sucursalNombre = sucursalRepository.observeSucursales().first()
            .find { it.id == sucursalId }
            ?.nombre

        val ahora = System.currentTimeMillis()
        val totales = cajaRepository.calcularTotales(sucursalId, inicioDelDia(), ahora)

        val inventario = inventarioRepository
            .observarInventario(sucursalId, busqueda = "", pagina = 1, tamanioPagina = TAMANIO_RESUMEN_INVENTARIO)
            .first()
            .items
            .map { item ->
                ArticuloResumenDto(
                    sku = item.articulo.sku,
                    nombre = item.articulo.nombre,
                    cantidad = item.cantidad.toPlainString(),
                    precioVenta = item.articulo.precioVenta.toPlainString(),
                    costo = item.articulo.costo?.toPlainString(),
                    ubicacion = item.ubicacion,
                )
            }

        val estado = EstadoPuntoVenta(
            sucursalId = sucursalId,
            sucursalNombre = sucursalNombre,
            caja = CajaResumenDto(
                totalVentas = totales.totalVentas.toPlainString(),
                totalEfectivo = totales.totalEfectivo.toPlainString(),
                totalTarjeta = totales.totalTarjeta.toPlainString(),
                totalRetiros = totales.totalRetiros.toPlainString(),
                montoEsperado = totales.montoEsperado.toPlainString(),
            ),
            inventario = inventario,
            historial = historial,
        )
        return json.encodeToString(estado)
    }

    private fun inicioDelDia(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
