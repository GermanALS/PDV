package com.pdv.pos.entrada

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.domain.model.Articulo
import com.pdv.pos.domain.model.ArticuloNuevo
import com.pdv.pos.domain.model.Entrada
import com.pdv.pos.domain.repository.EntradaRepository
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

// Mismo catalogo estatico de ejemplo que VentaViewModel (PLAN.md Parte 7,
// sub-paso 1) - se reemplaza por ArticuloRepository real en el sub-paso 2 de
// esta Parte.
private val CATALOGO_EJEMPLO = listOf(
    Articulo(
        id = "11111111-1111-4111-8111-111111111111",
        codigoBarras = "7501234567890",
        sku = "REF-001",
        nombre = "Refresco de cola 600ml",
        categoria = "Bebidas",
        unidadMedida = "pieza",
        precioVenta = BigDecimal("18.50"),
    ),
    Articulo(
        id = "22222222-2222-4222-8222-222222222222",
        codigoBarras = "7501234567906",
        sku = "PAN-002",
        nombre = "Pan de caja integral",
        categoria = "Panadería",
        unidadMedida = "pieza",
        precioVenta = BigDecimal("42.00"),
    ),
    Articulo(
        id = "33333333-3333-4333-8333-333333333333",
        codigoBarras = "7501234567913",
        sku = "LEC-003",
        nombre = "Leche entera 1L",
        categoria = "Lácteos",
        unidadMedida = "pieza",
        precioVenta = BigDecimal("27.90"),
    ),
    Articulo(
        id = "44444444-4444-4444-8444-444444444444",
        codigoBarras = "7501234567920",
        sku = "HUE-004",
        nombre = "Huevo blanco 12 pzas",
        categoria = "Lácteos",
        unidadMedida = "paquete",
        precioVenta = BigDecimal("55.00"),
    ),
    Articulo(
        id = "55555555-5555-4555-8555-555555555555",
        codigoBarras = "7501234567937",
        sku = "ARR-005",
        nombre = "Arroz 1kg",
        categoria = "Abarrotes",
        unidadMedida = "kg",
        precioVenta = BigDecimal("31.75"),
    ),
)

// No hay historial de entradas en esta etapa estatica del que derivar
// ubicaciones usadas - se toman las mismas de ejemplo que InventarioViewModel
// (Parte 9), ya que ambas pantallas leeran la misma tabla `inventario` una
// vez exista el repositorio real (sub-paso 2/3).
private val UBICACIONES_EJEMPLO = listOf("Estante A1", "Estante B2", "Refrigerador 1", "Estante C1")

@HiltViewModel
class EntradaViewModel @Inject constructor(
    private val entradaRepository: EntradaRepository,
    private val inventarioRepository: InventarioRepository,
    private val preferences: ConfiguracionPreferences,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        EntradaUiState(
            categoriasDisponibles = CATALOGO_EJEMPLO.mapNotNull { it.categoria }.distinct().sorted(),
            unidadesMedidaDisponibles = CATALOGO_EJEMPLO.map { it.unidadMedida }.distinct().sorted(),
            ubicacionesDisponibles = UBICACIONES_EJEMPLO,
        ),
    )
    val uiState: StateFlow<EntradaUiState> = _uiState.asStateFlow()

    fun onTipoChange(tipo: TipoEntrada) {
        _uiState.value = _uiState.value.copy(tipo = tipo, mensajeConfirmacion = null)
    }

    fun onBusquedaChange(valor: String) {
        _uiState.value = _uiState.value.copy(busqueda = valor)
    }

    // Busqueda real contra InventarioRepository (hallazgo de pruebas en el
    // Xiaomi, Parte 16): antes buscaba en CATALOGO_EJEMPLO, un catalogo
    // estatico de 5 articulos que nunca se reemplazo por el repositorio real
    // pese a que el propio comentario original lo daba por hecho desde la
    // Parte 8 - ni siquiera un articulo creado a mano aparecia en esta
    // busqueda. Misma consulta que ya usa InventarioViewModel (Parte 9) y el
    // contexto de la IA (Parte 15).
    fun buscarArticuloExistente() {
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

    fun onEscanearClick(objetivo: ObjetivoEscaneo) {
        _uiState.value = _uiState.value.copy(escaneando = objetivo)
    }

    fun onEscanerDismiss() {
        _uiState.value = _uiState.value.copy(escaneando = null)
    }

    fun onBarcodeEscaneado(codigo: String) {
        when (_uiState.value.escaneando) {
            ObjetivoEscaneo.BUSQUEDA_EXISTENTE -> {
                _uiState.value = _uiState.value.copy(escaneando = null, busqueda = codigo)
                buscarArticuloExistente()
            }
            ObjetivoEscaneo.CODIGO_BARRAS_NUEVO -> {
                _uiState.value = _uiState.value.copy(escaneando = null, codigoBarras = codigo)
            }
            null -> Unit
        }
    }

    fun onCodigoBarrasChange(valor: String) {
        _uiState.value = _uiState.value.copy(codigoBarras = valor)
    }

    fun onSkuChange(valor: String) {
        _uiState.value = _uiState.value.copy(sku = valor)
    }

    fun onNombreChange(valor: String) {
        _uiState.value = _uiState.value.copy(nombre = valor)
    }

    fun onDescripcionChange(valor: String) {
        _uiState.value = _uiState.value.copy(descripcion = valor)
    }

    fun onCategoriaChange(valor: String) {
        _uiState.value = _uiState.value.copy(categoria = valor)
    }

    fun onUnidadMedidaChange(valor: String) {
        _uiState.value = _uiState.value.copy(unidadMedida = valor)
    }

    fun onPrecioVentaChange(valor: String) {
        _uiState.value = _uiState.value.copy(precioVenta = valor)
    }

    fun onCostoChange(valor: String) {
        _uiState.value = _uiState.value.copy(costo = valor)
    }

    fun onCantidadChange(valor: String) {
        _uiState.value = _uiState.value.copy(cantidad = valor)
    }

    fun onUbicacionChange(valor: String) {
        _uiState.value = _uiState.value.copy(ubicacion = valor)
    }

    fun registrarEntrada() {
        viewModelScope.launch {
            val sucursalId = preferences.deviceConfig.first().sucursalIdSeleccionada
            val usuarioId = sessionManager.session.value?.username
            if (sucursalId == null || usuarioId == null) {
                _uiState.value = _uiState.value.copy(
                    mensajeConfirmacion = "No se pudo registrar la entrada: falta sucursal o sesión activa",
                )
                return@launch
            }

            val entrada = _uiState.value.construirEntrada(sucursalId = sucursalId, usuarioId = usuarioId)
                .getOrElse {
                    _uiState.value = _uiState.value.copy(mensajeConfirmacion = it.message)
                    return@launch
                }

            try {
                entradaRepository.registrarEntrada(entrada)
                _uiState.value = EntradaUiState(
                    tipo = _uiState.value.tipo,
                    categoriasDisponibles = _uiState.value.categoriasDisponibles,
                    unidadesMedidaDisponibles = _uiState.value.unidadesMedidaDisponibles,
                    ubicacionesDisponibles = _uiState.value.ubicacionesDisponibles,
                    mensajeConfirmacion = "Entrada registrada: ${entrada.cantidad} unidades",
                )
            } catch (e: IOException) {
                _uiState.value = _uiState.value.copy(mensajeConfirmacion = "No se pudo registrar la entrada: ${e.message}")
            } catch (e: HttpException) {
                _uiState.value = _uiState.value.copy(mensajeConfirmacion = "No se pudo registrar la entrada: ${e.message}")
            }
        }
    }

    fun descartarMensajeConfirmacion() {
        _uiState.value = _uiState.value.copy(mensajeConfirmacion = null)
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? =
    if (isBlank()) null else runCatching { BigDecimal(this) }.getOrNull()

private fun EntradaUiState.construirEntrada(sucursalId: String, usuarioId: String): Result<Entrada> {
    val cantidadValida = cantidad.toBigDecimalOrNull()
    if (cantidadValida == null || cantidadValida <= BigDecimal.ZERO) {
        return Result.failure(IllegalArgumentException("La cantidad debe ser un número mayor a 0"))
    }
    val ubicacionValida = ubicacion.trim().ifBlank { null }
    val ahora = System.currentTimeMillis()

    return when (tipo) {
        TipoEntrada.ARTICULO_EXISTENTE -> {
            val articulo = articuloEncontrado
                ?: return Result.failure(
                    IllegalArgumentException("Selecciona un artículo existente antes de registrar la entrada"),
                )
            Result.success(
                Entrada.DeArticuloExistente(
                    id = UUID.randomUUID().toString(),
                    sucursalId = sucursalId,
                    usuarioId = usuarioId,
                    fecha = ahora,
                    cantidad = cantidadValida,
                    ubicacion = ubicacionValida,
                    articuloId = articulo.id,
                ),
            )
        }
        TipoEntrada.ARTICULO_NUEVO -> {
            if (sku.isBlank() || nombre.isBlank() || unidadMedida.isBlank() || precioVenta.isBlank()) {
                return Result.failure(
                    IllegalArgumentException("Completa SKU, nombre, unidad de medida y precio de venta"),
                )
            }
            val precioValido = precioVenta.toBigDecimalOrNull()
                ?: return Result.failure(IllegalArgumentException("El precio de venta debe ser un número válido"))
            val costoValido = costo.toBigDecimalOrNull()
            Result.success(
                Entrada.DeArticuloNuevo(
                    id = UUID.randomUUID().toString(),
                    sucursalId = sucursalId,
                    usuarioId = usuarioId,
                    fecha = ahora,
                    cantidad = cantidadValida,
                    ubicacion = ubicacionValida,
                    articulo = ArticuloNuevo(
                        id = UUID.randomUUID().toString(),
                        codigoBarras = codigoBarras.trim().ifBlank { null },
                        sku = sku,
                        nombre = nombre,
                        descripcion = descripcion.trim().ifBlank { null },
                        categoria = categoria.trim().ifBlank { null },
                        unidadMedida = unidadMedida,
                        precioVenta = precioValido,
                        costo = costoValido,
                    ),
                ),
            )
        }
    }
}
