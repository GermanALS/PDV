package com.pdv.pos.devolucion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.domain.model.Articulo
import com.pdv.pos.domain.model.Devolucion
import com.pdv.pos.domain.model.DevolucionLinea
import com.pdv.pos.domain.repository.DevolucionRepository
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

// Catalogo estatico de ejemplo del sub-paso 1 (UI) de la Parte 11 - mismo
// criterio que CATALOGO_EJEMPLO en VentaViewModel (PLAN.md Parte 7): se
// reemplaza por busqueda real de articulos en un sub-paso posterior si el
// checklist de una Parte futura lo pide, no antes.
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
)

@HiltViewModel
class DevolucionViewModel @Inject constructor(
    private val devolucionRepository: DevolucionRepository,
    private val preferences: ConfiguracionPreferences,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DevolucionUiState())
    val uiState: StateFlow<DevolucionUiState> = _uiState.asStateFlow()

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

    fun onCantidadChange(valor: String) {
        _uiState.value = _uiState.value.copy(cantidadIngresada = valor)
    }

    fun onMotivoChange(valor: String) {
        _uiState.value = _uiState.value.copy(motivoIngresado = valor)
    }

    fun onCondicionSelected(condicion: Condicion) {
        _uiState.value = _uiState.value.copy(condicionSeleccionada = condicion)
    }

    fun agregarLinea() {
        val estado = _uiState.value
        val articulo = estado.articuloEncontrado ?: return
        val cantidad = estado.cantidadIngresada.toBigDecimalOrNull()
        if (cantidad == null || cantidad <= BigDecimal.ZERO) {
            _uiState.value = estado.copy(errorLinea = "Ingresa una cantidad válida")
            return
        }
        val linea = LineaDevolucion(
            id = UUID.randomUUID().toString(),
            articulo = articulo,
            cantidad = cantidad,
            motivo = estado.motivoIngresado.trim().ifBlank { null },
            condicion = estado.condicionSeleccionada,
        )
        _uiState.value = estado.copy(
            lineas = estado.lineas + linea,
            busqueda = "",
            articuloEncontrado = null,
            cantidadIngresada = "1",
            motivoIngresado = "",
            condicionSeleccionada = Condicion.DEFECTUOSO,
            errorLinea = null,
        )
    }

    fun quitarLinea(lineaId: String) {
        _uiState.value = _uiState.value.copy(
            lineas = _uiState.value.lineas.filterNot { it.id == lineaId },
        )
    }

    fun onVentaOriginalChange(valor: String) {
        _uiState.value = _uiState.value.copy(ventaOriginal = valor)
    }

    fun registrarDevolucion() {
        val estado = _uiState.value
        if (estado.lineas.isEmpty()) return
        viewModelScope.launch {
            val credenciales = obtenerSucursalYUsuario()
            if (credenciales == null) {
                _uiState.value = _uiState.value.copy(
                    mensajeConfirmacion = "No se pudo registrar la devolución: falta sucursal o sesión activa",
                )
                return@launch
            }
            val (sucursalId, usuarioId) = credenciales
            val devolucion = estado.toDevolucion(sucursalId = sucursalId, usuarioId = usuarioId)
            try {
                devolucionRepository.registrarDevolucion(devolucion)
            } catch (e: IOException) {
                _uiState.value = _uiState.value.copy(
                    mensajeConfirmacion = "No se pudo registrar la devolución: ${e.message}",
                )
                return@launch
            } catch (e: HttpException) {
                _uiState.value = _uiState.value.copy(
                    mensajeConfirmacion = "No se pudo registrar la devolución: ${e.message}",
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                lineas = emptyList(),
                ventaOriginal = "",
                mensajeConfirmacion = "Devolución registrada: folio ${devolucion.folio}",
                historial = listOf(
                    DevolucionRegistrada(devolucion.folio, devolucion.lineas.size, devolucion.estado),
                ) + _uiState.value.historial,
            )
        }
    }

    fun descartarMensajeConfirmacion() {
        _uiState.value = _uiState.value.copy(mensajeConfirmacion = null)
    }

    private suspend fun obtenerSucursalYUsuario(): Pair<String, String>? {
        val sucursalId = preferences.deviceConfig.first().sucursalIdSeleccionada
        val usuarioId = sessionManager.session.value?.username
        return if (sucursalId != null && usuarioId != null) sucursalId to usuarioId else null
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? =
    if (isBlank()) null else runCatching { BigDecimal(this) }.getOrNull()

private fun DevolucionUiState.toDevolucion(sucursalId: String, usuarioId: String): Devolucion {
    val ahora = System.currentTimeMillis()
    return Devolucion(
        id = UUID.randomUUID().toString(),
        sucursalId = sucursalId,
        usuarioId = usuarioId,
        ventaId = ventaOriginal.trim().ifBlank { null },
        folio = "D-$ahora",
        fecha = ahora,
        estado = "registrada",
        lineas = lineas.map { it.toDevolucionLinea() },
    )
}

private fun LineaDevolucion.toDevolucionLinea() = DevolucionLinea(
    articuloId = articulo.id,
    cantidad = cantidad,
    motivo = motivo,
    condicion = condicion.aTextoDominio(),
)
