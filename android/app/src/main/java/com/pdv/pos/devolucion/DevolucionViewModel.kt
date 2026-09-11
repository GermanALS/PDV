package com.pdv.pos.devolucion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.domain.model.Devolucion
import com.pdv.pos.domain.model.DevolucionLinea
import com.pdv.pos.domain.repository.DevolucionRepository
import com.pdv.pos.domain.repository.InventarioRepository
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

@HiltViewModel
class DevolucionViewModel @Inject constructor(
    private val devolucionRepository: DevolucionRepository,
    private val inventarioRepository: InventarioRepository,
    private val preferences: ConfiguracionPreferences,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DevolucionUiState())
    val uiState: StateFlow<DevolucionUiState> = _uiState.asStateFlow()

    fun onBusquedaChange(valor: String) {
        _uiState.value = _uiState.value.copy(busqueda = valor)
    }

    // Busqueda real contra InventarioRepository (hallazgo de pruebas en el
    // Xiaomi, Parte 32): antes buscaba en CATALOGO_EJEMPLO, un catalogo
    // estatico de 3 articulos que nunca se reemplazo pese a que Venta y
    // Entrada ya habian recibido este mismo fix en la Parte 16.
    fun buscar() {
        val termino = _uiState.value.busqueda.trim()
        if (termino.isEmpty()) {
            _uiState.value = _uiState.value.copy(articuloEncontrado = null, errorBusqueda = null)
            return
        }
        viewModelScope.launch {
            val sucursalId = preferences.deviceConfig.first().sucursalIdSeleccionada
            if (sucursalId == null) {
                _uiState.value = _uiState.value.copy(
                    articuloEncontrado = null,
                    errorBusqueda = "Selecciona una sucursal en Configuración",
                )
                return@launch
            }
            val encontrado = inventarioRepository
                .observarInventario(sucursalId, busqueda = termino, pagina = 1, tamanioPagina = 1)
                .first()
                .items
                .firstOrNull()
                ?.articulo
            _uiState.value = _uiState.value.copy(
                articuloEncontrado = encontrado,
                errorBusqueda = if (encontrado == null) "Artículo no encontrado" else null,
            )
        }
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
