package com.pdv.pos.inventario

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.domain.model.EdicionArticulo
import com.pdv.pos.domain.model.InventarioItem
import com.pdv.pos.domain.model.PaginaInventario
import com.pdv.pos.domain.repository.InventarioRepository
import com.pdv.pos.inventario.export.ArchivoExportado
import com.pdv.pos.inventario.export.InventarioExportManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.math.BigDecimal
import javax.inject.Inject

// Parametros de consulta separados del InventarioUiState expuesto: son la
// unica entrada de la que depende el flatMapLatest hacia el repositorio, asi
// que nunca se retroalimentan con "resultado" (evita un ciclo reactivo).
private data class ParametrosConsulta(
    val busqueda: String = "",
    val pagina: Int = 1,
    val tamanioPagina: Int = TAMANIOS_PAGINA_DISPONIBLES.first(),
    // Se incrementa tras un actualizarArticulo exitoso para forzar un
    // refetch en modo REMOTO, donde observarInventario no es reactivo de
    // verdad (RemoteInventarioRepository, PLAN.md Parte 9, sub-paso 3) - en
    // modo local el Flow de Room ya se refresca solo.
    val version: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InventarioViewModel @Inject constructor(
    private val inventarioRepository: InventarioRepository,
    private val preferences: ConfiguracionPreferences,
    private val sessionManager: SessionManager,
    private val exportManager: InventarioExportManager,
) : ViewModel() {

    private val parametros = MutableStateFlow(ParametrosConsulta())
    private val sucursalId = preferences.deviceConfig.map { it.sucursalIdSeleccionada }.distinctUntilChanged()

    private val _uiState = MutableStateFlow(InventarioUiState())
    val uiState: StateFlow<InventarioUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(sucursalId, parametros) { id, params -> id to params }
                .flatMapLatest { (id, params) ->
                    if (id == null) {
                        flowOf(PaginaInventario(items = emptyList(), pagina = params.pagina, tamanioPagina = params.tamanioPagina, total = 0))
                    } else {
                        inventarioRepository.observarInventario(id, params.busqueda, params.pagina, params.tamanioPagina)
                    }
                }
                .collect { resultado -> _uiState.update { it.copy(resultado = resultado) } }
        }
        viewModelScope.launch {
            inventarioRepository.observarCategorias()
                .collect { categorias -> _uiState.update { it.copy(categoriasDisponibles = categorias) } }
        }
        viewModelScope.launch {
            inventarioRepository.observarUnidadesMedida()
                .collect { unidades -> _uiState.update { it.copy(unidadesMedidaDisponibles = unidades) } }
        }
        viewModelScope.launch {
            sucursalId
                .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else inventarioRepository.observarUbicaciones(id) }
                .collect { ubicaciones -> _uiState.update { it.copy(ubicacionesDisponibles = ubicaciones) } }
        }
    }

    fun onBusquedaChange(valor: String) {
        _uiState.update { it.copy(busqueda = valor) }
        parametros.update { it.copy(busqueda = valor, pagina = 1) }
    }

    fun onEscanearClick() {
        _uiState.update { it.copy(escaneando = true) }
    }

    fun onEscanerDismiss() {
        _uiState.update { it.copy(escaneando = false) }
    }

    fun onBarcodeEscaneado(codigo: String) {
        _uiState.update { it.copy(escaneando = false) }
        onBusquedaChange(codigo)
    }

    fun onTamanioPaginaChange(tamanio: Int) {
        parametros.update { it.copy(tamanioPagina = tamanio, pagina = 1) }
    }

    fun paginaAnterior() {
        parametros.update { it.copy(pagina = (_uiState.value.paginaMostrada - 1).coerceAtLeast(1)) }
    }

    fun paginaSiguiente() {
        parametros.update { it.copy(pagina = (_uiState.value.paginaMostrada + 1).coerceAtMost(_uiState.value.totalPaginas)) }
    }

    fun onEditarClick(item: InventarioItem) {
        _uiState.update {
            it.copy(
                edicion = EdicionArticuloUiState(
                    articuloId = item.articulo.id,
                    nombre = item.articulo.nombre,
                    descripcion = item.articulo.descripcion.orEmpty(),
                    categoria = item.articulo.categoria.orEmpty(),
                    unidadMedida = item.articulo.unidadMedida,
                    precioVenta = item.articulo.precioVenta.toPlainString(),
                    costo = item.articulo.costo?.toPlainString().orEmpty(),
                    cantidadActual = item.cantidad,
                    cantidad = item.cantidad.formatoCantidad(),
                    ubicacion = item.ubicacion.orEmpty(),
                ),
            )
        }
    }

    fun onEdicionDismiss() {
        _uiState.update { it.copy(edicion = null) }
    }

    fun onEdicionNombreChange(valor: String) = actualizarEdicion { it.copy(nombre = valor) }
    fun onEdicionDescripcionChange(valor: String) = actualizarEdicion { it.copy(descripcion = valor) }
    fun onEdicionCategoriaChange(valor: String) = actualizarEdicion { it.copy(categoria = valor) }
    fun onEdicionUnidadMedidaChange(valor: String) = actualizarEdicion { it.copy(unidadMedida = valor) }
    fun onEdicionPrecioVentaChange(valor: String) = actualizarEdicion { it.copy(precioVenta = valor) }
    fun onEdicionCostoChange(valor: String) = actualizarEdicion { it.copy(costo = valor) }
    fun onEdicionCantidadChange(valor: String) = actualizarEdicion { it.copy(cantidad = valor) }
    fun onEdicionUbicacionChange(valor: String) = actualizarEdicion { it.copy(ubicacion = valor) }

    private fun actualizarEdicion(transform: (EdicionArticuloUiState) -> EdicionArticuloUiState) {
        val actual = _uiState.value.edicion ?: return
        _uiState.update { it.copy(edicion = transform(actual)) }
    }

    fun guardarCambios() {
        val edicionUi = _uiState.value.edicion ?: return
        viewModelScope.launch {
            val sucursalIdSeleccionada = preferences.deviceConfig.first().sucursalIdSeleccionada
            val usuarioId = sessionManager.session.value?.username
            if (sucursalIdSeleccionada == null || usuarioId == null) {
                _uiState.update { it.copy(mensajeConfirmacion = "No se pudo actualizar: falta sucursal o sesión activa") }
                return@launch
            }

            val edicion = edicionUi.construirEdicion(sucursalId = sucursalIdSeleccionada, usuarioId = usuarioId)
                .getOrElse {
                    _uiState.update { current -> current.copy(mensajeConfirmacion = it.message) }
                    return@launch
                }

            try {
                inventarioRepository.actualizarArticulo(edicion)
                parametros.update { it.copy(version = it.version + 1) }
                _uiState.update { it.copy(edicion = null, mensajeConfirmacion = "Artículo actualizado: ${edicion.nombre}") }
            } catch (e: IOException) {
                _uiState.update { it.copy(mensajeConfirmacion = "No se pudo actualizar el artículo: ${e.message}") }
            } catch (e: HttpException) {
                _uiState.update { it.copy(mensajeConfirmacion = "No se pudo actualizar el artículo: ${e.message}") }
            }
        }
    }

    fun descartarMensajeConfirmacion() {
        _uiState.update { it.copy(mensajeConfirmacion = null) }
    }

    fun exportarCsv() = exportar { exportManager.exportarCsv(it) }

    fun exportarExcel() = exportar { exportManager.exportarExcel(it) }

    private fun exportar(generar: suspend (List<InventarioItem>) -> ArchivoExportado) {
        viewModelScope.launch {
            _uiState.update { it.copy(exportando = true) }
            val items = obtenerTodosLosItemsFiltrados()
            val archivo = generar(items)
            _uiState.update { it.copy(exportando = false, archivoParaCompartir = archivo) }
        }
    }

    // La exportacion cubre todo lo que coincide con la busqueda activa, no
    // solo la pagina visible - recorre las paginas del repositorio (real
    // desde el sub-paso 2/3, ya no el catalogo estatico del sub-paso 1).
    private suspend fun obtenerTodosLosItemsFiltrados(): List<InventarioItem> {
        val id = preferences.deviceConfig.first().sucursalIdSeleccionada ?: return emptyList()
        val busqueda = _uiState.value.busqueda
        val tamanioPagina = 100
        val items = mutableListOf<InventarioItem>()
        var pagina = 1
        while (true) {
            val resultado = inventarioRepository.observarInventario(id, busqueda, pagina, tamanioPagina).first()
            items += resultado.items
            if (resultado.items.isEmpty() || items.size >= resultado.total) break
            pagina++
        }
        return items
    }

    fun onArchivoCompartido() {
        _uiState.update { it.copy(archivoParaCompartir = null) }
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? =
    if (isBlank()) null else runCatching { BigDecimal(this) }.getOrNull()

private fun EdicionArticuloUiState.construirEdicion(sucursalId: String, usuarioId: String): Result<EdicionArticulo> {
    if (nombre.isBlank() || unidadMedida.isBlank()) {
        return Result.failure(IllegalArgumentException("Completa nombre y unidad de medida"))
    }
    val precioValido = precioVenta.toBigDecimalOrNull()
        ?: return Result.failure(IllegalArgumentException("El precio de venta debe ser un número válido"))
    val cantidadValida = cantidad.toBigDecimalOrNull()
        ?: return Result.failure(IllegalArgumentException("La cantidad debe ser un número válido"))
    val costoValido = costo.toBigDecimalOrNull()

    return Result.success(
        EdicionArticulo(
            articuloId = articuloId,
            sucursalId = sucursalId,
            usuarioId = usuarioId,
            nombre = nombre,
            descripcion = descripcion.trim().ifBlank { null },
            categoria = categoria.trim().ifBlank { null },
            unidadMedida = unidadMedida,
            precioVenta = precioValido,
            costo = costoValido,
            nuevaCantidad = cantidadValida,
            ubicacion = ubicacion.trim().ifBlank { null },
        ),
    )
}
