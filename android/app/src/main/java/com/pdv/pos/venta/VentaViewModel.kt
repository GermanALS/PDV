package com.pdv.pos.venta

import androidx.lifecycle.ViewModel
import com.pdv.pos.domain.model.Articulo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal
import javax.inject.Inject

// Catalogo estatico de ejemplo del sub-paso 1 (UI) de la Parte 7 - se
// reemplaza por LocalArticuloRepository/RemoteArticuloRepository reales en
// el sub-paso 2 (docs/PLAN.md Parte 7, checklist "Repositorio local").
private val CATALOGO_EJEMPLO = listOf(
    Articulo(
        id = "art-1",
        codigoBarras = "7501234567890",
        sku = "REF-001",
        nombre = "Refresco de cola 600ml",
        unidadMedida = "pieza",
        precioVenta = BigDecimal("18.50"),
    ),
    Articulo(
        id = "art-2",
        codigoBarras = "7501234567906",
        sku = "PAN-002",
        nombre = "Pan de caja integral",
        unidadMedida = "pieza",
        precioVenta = BigDecimal("42.00"),
    ),
    Articulo(
        id = "art-3",
        codigoBarras = "7501234567913",
        sku = "LEC-003",
        nombre = "Leche entera 1L",
        unidadMedida = "pieza",
        precioVenta = BigDecimal("27.90"),
    ),
    Articulo(
        id = "art-4",
        codigoBarras = "7501234567920",
        sku = "HUE-004",
        nombre = "Huevo blanco 12 pzas",
        unidadMedida = "paquete",
        precioVenta = BigDecimal("55.00"),
    ),
    Articulo(
        id = "art-5",
        codigoBarras = "7501234567937",
        sku = "ARR-005",
        nombre = "Arroz 1kg",
        unidadMedida = "kg",
        precioVenta = BigDecimal("31.75"),
    ),
)

@HiltViewModel
class VentaViewModel @Inject constructor() : ViewModel() {

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
        if (_uiState.value.carrito.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            carrito = emptyList(),
            articuloEncontrado = null,
            busqueda = "",
            mensajeConfirmacion = "Venta registrada (ejemplo, sin persistencia todavía)",
        )
    }

    fun descartarMensajeConfirmacion() {
        _uiState.value = _uiState.value.copy(mensajeConfirmacion = null)
    }
}
