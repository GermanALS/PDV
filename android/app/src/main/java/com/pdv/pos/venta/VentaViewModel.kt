package com.pdv.pos.venta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.domain.model.Articulo
import com.pdv.pos.domain.model.Venta
import com.pdv.pos.domain.model.VentaLinea
import com.pdv.pos.domain.repository.VentaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.HttpException
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
        _uiState.value = _uiState.value.copy(carrito = carritoActualizado)
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
            val sucursalId = preferences.deviceConfig.first().sucursalIdSeleccionada
            val usuarioId = sessionManager.session.value?.username
            if (sucursalId == null || usuarioId == null) {
                _uiState.value = _uiState.value.copy(
                    mensajeConfirmacion = "No se pudo registrar la venta: falta sucursal o sesión activa",
                )
                return@launch
            }
            val venta = estado.toVenta(sucursalId = sucursalId, usuarioId = usuarioId)
            try {
                ventaRepository.registrarVenta(venta)
                _uiState.value = _uiState.value.copy(
                    carrito = emptyList(),
                    articuloEncontrado = null,
                    busqueda = "",
                    mensajeConfirmacion = "Venta registrada: folio ${venta.folio}",
                )
            } catch (e: IOException) {
                _uiState.value = _uiState.value.copy(mensajeConfirmacion = "No se pudo registrar la venta: ${e.message}")
            } catch (e: HttpException) {
                _uiState.value = _uiState.value.copy(mensajeConfirmacion = "No se pudo registrar la venta: ${e.message}")
            }
        }
    }

    fun descartarMensajeConfirmacion() {
        _uiState.value = _uiState.value.copy(mensajeConfirmacion = null)
    }
}

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
