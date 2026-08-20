package com.pdv.pos.venta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.domain.model.Articulo
import com.pdv.pos.domain.model.Venta
import com.pdv.pos.domain.model.VentaLinea
import com.pdv.pos.domain.repository.SucursalRepository
import com.pdv.pos.domain.repository.VentaRepository
import com.pdv.pos.venta.ticket.TicketFormatter
import com.pdv.pos.venta.ticket.TicketManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject

// Catalogo estatico de ejemplo del sub-paso 1 (UI) de la Parte 7 - se
// reemplaza por LocalArticuloRepository/RemoteArticuloRepository reales en
// el sub-paso 2 (docs/PLAN.md Parte 7, checklist "Repositorio local"). Los
// id son UUID (no "art-1") porque desde el sub-paso 4 (Wiring) fluyen tal
// cual a VentaLinea.articuloId, y el backend valida articulo_id como UUID
// (backend/app/schemas/venta.py) - un id no-UUID rompe toda venta en modo
// REMOTO con 422 (hallazgo de code-reviewer).
private val CATALOGO_EJEMPLO = listOf(
    Articulo(
        id = "11111111-1111-4111-8111-111111111111",
        codigoBarras = "7501234567890",
        sku = "REF-001",
        nombre = "Refresco de cola 600ml",
        unidadMedida = "pieza",
        precioVenta = BigDecimal("18.50"),
    ),
    Articulo(
        id = "22222222-2222-4222-8222-222222222222",
        codigoBarras = "7501234567906",
        sku = "PAN-002",
        nombre = "Pan de caja integral",
        unidadMedida = "pieza",
        precioVenta = BigDecimal("42.00"),
    ),
    Articulo(
        id = "33333333-3333-4333-8333-333333333333",
        codigoBarras = "7501234567913",
        sku = "LEC-003",
        nombre = "Leche entera 1L",
        unidadMedida = "pieza",
        precioVenta = BigDecimal("27.90"),
    ),
    Articulo(
        id = "44444444-4444-4444-8444-444444444444",
        codigoBarras = "7501234567920",
        sku = "HUE-004",
        nombre = "Huevo blanco 12 pzas",
        unidadMedida = "paquete",
        precioVenta = BigDecimal("55.00"),
    ),
    Articulo(
        id = "55555555-5555-4555-8555-555555555555",
        codigoBarras = "7501234567937",
        sku = "ARR-005",
        nombre = "Arroz 1kg",
        unidadMedida = "kg",
        precioVenta = BigDecimal("31.75"),
    ),
)

@HiltViewModel
class VentaViewModel @Inject constructor(
    private val ventaRepository: VentaRepository,
    private val preferences: ConfiguracionPreferences,
    private val sessionManager: SessionManager,
    private val sucursalRepository: SucursalRepository,
    private val ticketManager: TicketManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VentaUiState())
    val uiState: StateFlow<VentaUiState> = _uiState.asStateFlow()

    fun onBusquedaChange(valor: String) {
        _uiState.value = _uiState.value.copy(busqueda = valor)
    }

    fun buscar() {
        val termino = _uiState.value.busqueda.trim()
        val encontrado = if (termino.isEmpty()) {
            null
        } else {
            CATALOGO_EJEMPLO.firstOrNull {
                it.codigoBarras == termino ||
                    it.sku.equals(termino, ignoreCase = true) ||
                    it.nombre.contains(termino, ignoreCase = true)
            }
        }
        _uiState.value = _uiState.value.copy(
            articuloEncontrado = encontrado,
            errorBusqueda = if (encontrado == null && termino.isNotEmpty()) "Artículo no encontrado" else null,
        )
    }

    fun onEscanearClick() {
        _uiState.value = _uiState.value.copy(mostrarEscaner = true)
    }

    fun onEscanerDismiss() {
        _uiState.value = _uiState.value.copy(mostrarEscaner = false)
    }

    fun onBarcodeEscaneado(codigo: String) {
        _uiState.value = _uiState.value.copy(mostrarEscaner = false, busqueda = codigo)
        buscar()
    }

    fun agregarAlCarrito() {
        val articulo = _uiState.value.articuloEncontrado ?: return
        val carritoActual = _uiState.value.carrito
        val yaEnCarrito = carritoActual.find { it.articulo.id == articulo.id }
        val carritoActualizado = if (yaEnCarrito != null) {
            carritoActual.map {
                if (it.articulo.id == articulo.id) it.copy(cantidad = it.cantidad + 1) else it
            }
        } else {
            carritoActual + LineaCarrito(articulo = articulo, cantidad = 1)
        }
        // Limpia el resultado de la venta anterior (cambio, ticket, mensaje) al
        // empezar a construir un carrito nuevo - de lo contrario el cambio y el
        // boton de impresion de la venta previa siguen visibles mientras se arma
        // la siguiente, con riesgo de que el encargado de turno entregue el
        // cambio o reimprima el ticket equivocado (hallazgo de code-reviewer).
        _uiState.value = _uiState.value.copy(
            carrito = carritoActualizado,
            cambioEntregado = null,
            ticketPdf = null,
            mensajeConfirmacion = null,
        )
    }

    fun quitarDelCarrito(articuloId: String) {
        _uiState.value = _uiState.value.copy(
            carrito = _uiState.value.carrito.filterNot { it.articulo.id == articuloId },
        )
    }

    fun cambiarCantidad(articuloId: String, cantidad: Int) {
        if (cantidad < 1) return
        _uiState.value = _uiState.value.copy(
            carrito = _uiState.value.carrito.map {
                if (it.articulo.id == articuloId) it.copy(cantidad = cantidad) else it
            },
        )
    }

    fun onMetodoPagoSelected(metodoPago: MetodoPago) {
        _uiState.value = _uiState.value.copy(metodoPago = metodoPago)
    }

    fun confirmarVenta() {
        val estado = _uiState.value
        if (estado.carrito.isEmpty()) return
        viewModelScope.launch {
            val credenciales = obtenerSucursalYUsuario()
            if (credenciales == null) {
                _uiState.value = _uiState.value.copy(
                    mensajeConfirmacion = "No se pudo registrar la venta: falta sucursal o sesión activa",
                )
                return@launch
            }
            if (estado.metodoPago == MetodoPago.EFECTIVO) {
                _uiState.value = _uiState.value.copy(mostrarDialogoEfectivo = true)
            } else {
                val (sucursalId, usuarioId) = credenciales
                registrarVentaYGenerarTicket(estado, sucursalId, usuarioId, efectivoRecibido = null, cambio = null)
            }
        }
    }

    fun onEfectivoIngresadoChange(valor: String) {
        _uiState.value = _uiState.value.copy(efectivoIngresado = valor, errorEfectivo = null)
    }

    fun confirmarEfectivo() {
        val estado = _uiState.value
        val montoRecibido = estado.efectivoIngresado.toBigDecimalOrNull()
        if (montoRecibido == null || montoRecibido < estado.total) {
            _uiState.value = _uiState.value.copy(
                errorEfectivo = "Ingresa un monto válido, mayor o igual al total",
            )
            return
        }
        viewModelScope.launch {
            val credenciales = obtenerSucursalYUsuario()
            if (credenciales == null) {
                _uiState.value = _uiState.value.copy(
                    mostrarDialogoEfectivo = false,
                    mensajeConfirmacion = "No se pudo registrar la venta: falta sucursal o sesión activa",
                )
                return@launch
            }
            val (sucursalId, usuarioId) = credenciales
            registrarVentaYGenerarTicket(
                estado = estado,
                sucursalId = sucursalId,
                usuarioId = usuarioId,
                efectivoRecibido = montoRecibido,
                cambio = montoRecibido - estado.total,
            )
        }
    }

    fun cancelarDialogoEfectivo() {
        _uiState.value = _uiState.value.copy(
            mostrarDialogoEfectivo = false,
            efectivoIngresado = "",
            errorEfectivo = null,
        )
    }

    fun onTicketImpreso() {
        _uiState.value = _uiState.value.copy(mostrarDialogoReimpresion = true)
    }

    fun onReimpresionDescartada() {
        _uiState.value = _uiState.value.copy(mostrarDialogoReimpresion = false).sinResultadoDeVenta()
    }

    // Boton "Cerrar venta": para cuando no hace falta o no es posible
    // imprimir y solo se quiere volver a una pantalla igual a la inicial.
    fun cerrarVenta() {
        _uiState.value = _uiState.value.sinResultadoDeVenta()
    }

    private suspend fun obtenerSucursalYUsuario(): Pair<String, String>? {
        val sucursalId = preferences.deviceConfig.first().sucursalIdSeleccionada
        val usuarioId = sessionManager.session.value?.username
        return if (sucursalId != null && usuarioId != null) sucursalId to usuarioId else null
    }

    private suspend fun registrarVentaYGenerarTicket(
        estado: VentaUiState,
        sucursalId: String,
        usuarioId: String,
        efectivoRecibido: BigDecimal?,
        cambio: BigDecimal?,
    ) {
        val venta = estado.toVenta(sucursalId = sucursalId, usuarioId = usuarioId)
        try {
            ventaRepository.registrarVenta(venta)
        } catch (e: IOException) {
            _uiState.value = _uiState.value.copy(
                mensajeConfirmacion = "No se pudo registrar la venta: ${e.message}",
                mostrarDialogoEfectivo = false,
            )
            return
        } catch (e: HttpException) {
            _uiState.value = _uiState.value.copy(
                mensajeConfirmacion = "No se pudo registrar la venta: ${e.message}",
                mostrarDialogoEfectivo = false,
            )
            return
        }
        // La venta ya quedo registrada: se limpia el carrito de inmediato para
        // que un fallo posterior generando el ticket (I/O de archivo) no se
        // confunda con una venta fallida y el encargado la reintente duplicada.
        _uiState.value = _uiState.value.copy(
            carrito = emptyList(),
            articuloEncontrado = null,
            busqueda = "",
            mensajeConfirmacion = "Venta registrada: folio ${venta.folio}",
            cambioEntregado = cambio,
            ticketPdf = null,
            mostrarDialogoEfectivo = false,
            efectivoIngresado = "",
            errorEfectivo = null,
        )
        val ticket = generarTicketSeguro(venta, sucursalId, usuarioId, estado, efectivoRecibido, cambio)
        if (ticket != null) {
            _uiState.value = _uiState.value.copy(ticketPdf = ticket)
        }
    }

    private suspend fun generarTicketSeguro(
        venta: Venta,
        sucursalId: String,
        usuarioId: String,
        estado: VentaUiState,
        efectivoRecibido: BigDecimal?,
        cambio: BigDecimal?,
    ): File? = try {
        val sucursal = sucursalRepository.observeSucursales().first().find { it.id == sucursalId }
        val lineas = TicketFormatter.generarLineas(
            folio = venta.folio,
            fecha = venta.fecha,
            sucursalNombre = sucursal?.nombre ?: sucursalId,
            sucursalDireccion = sucursal?.direccion,
            usuarioId = usuarioId,
            carrito = estado.carrito,
            subtotal = estado.subtotal,
            descuento = estado.descuento,
            impuestos = estado.impuestos,
            total = estado.total,
            metodoPago = estado.metodoPago,
            efectivoRecibido = efectivoRecibido,
            cambio = cambio,
        )
        ticketManager.generarTicket(venta.folio, venta.fecha, lineas)
    } catch (e: IOException) {
        null
    }

    fun descartarMensajeConfirmacion() {
        _uiState.value = _uiState.value.copy(mensajeConfirmacion = null)
    }
}

// Mismo helper que InventarioViewModel.kt/EntradaViewModel.kt (parseo seguro
// de BigDecimal desde entrada de usuario) - sin utilidad compartida en el
// proyecto todavia, se mantiene la misma duplicacion por archivo ya
// establecida en esos dos modulos.
private fun String.toBigDecimalOrNull(): BigDecimal? =
    if (isBlank()) null else runCatching { BigDecimal(this) }.getOrNull()

private fun VentaUiState.sinResultadoDeVenta(): VentaUiState = copy(
    mensajeConfirmacion = null,
    cambioEntregado = null,
    ticketPdf = null,
)

private fun VentaUiState.toVenta(sucursalId: String, usuarioId: String): Venta {
    val ahora = System.currentTimeMillis()
    return Venta(
        id = UUID.randomUUID().toString(),
        sucursalId = sucursalId,
        usuarioId = usuarioId,
        folio = "V-$ahora",
        fecha = ahora,
        subtotal = subtotal,
        descuento = descuento,
        impuestos = impuestos,
        total = total,
        metodoPago = metodoPago.aTextoDominio(),
        estado = "completada",
        lineas = carrito.map { it.toVentaLinea() },
    )
}

private fun LineaCarrito.toVentaLinea() = VentaLinea(
    articuloId = articulo.id,
    cantidad = BigDecimal(cantidad),
    precioUnitario = articulo.precioVenta,
    subtotal = subtotal,
)

private fun MetodoPago.aTextoDominio(): String = when (this) {
    MetodoPago.EFECTIVO -> "efectivo"
    MetodoPago.TARJETA -> "tarjeta"
}
