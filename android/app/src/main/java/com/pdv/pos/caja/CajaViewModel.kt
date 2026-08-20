package com.pdv.pos.caja

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.domain.model.CorteCaja
import com.pdv.pos.domain.model.RetiroEfectivo
import com.pdv.pos.domain.repository.CajaRepository
import com.pdv.pos.domain.repository.RetiroEfectivoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.math.BigDecimal
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CajaViewModel @Inject constructor(
    private val cajaRepository: CajaRepository,
    private val retiroRepository: RetiroEfectivoRepository,
    private val preferences: ConfiguracionPreferences,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(estadoInicial())
    val uiState = _uiState.asStateFlow()

    fun onTipoCorteChange(tipo: TipoCorte) {
        val estado = _uiState.value
        val (inicio, fin) = periodoPorDefecto(tipo, estado.historialCortes)
        _uiState.value = estado.copy(tipoCorte = tipo, fechaInicio = inicio, fechaFin = fin, calculado = false)
    }

    fun onFechaInicioChange(millis: Long) {
        if (!_uiState.value.periodoEditable) return
        _uiState.value = _uiState.value.copy(calculado = false, fechaInicio = millis)
    }

    fun onFechaFinChange(millis: Long) {
        if (!_uiState.value.periodoEditable) return
        _uiState.value = _uiState.value.copy(calculado = false, fechaFin = millis)
    }

    fun onCalcularClick() {
        // El periodo del corte parcial es "hasta la hora de solicitud"
        // (PLAN.md Parte 10): se recalcula aqui, no se reutiliza el que
        // quedo en el estado desde la ultima vez que se fijo (carga de
        // pantalla, cambio de tipo, o el guardado anterior). De lo
        // contrario un retiro registrado despues de ese momento cae fuera
        // del rango consultado y el corte no lo refleja hasta el siguiente
        // ciclo de calculo (bug encontrado via el log de la app en el
        // Xiaomi: DB_WRITE del retiro presente, pero monto_esperado sin
        // cambios). El corte final no se toca: su periodo es editable por
        // el usuario a proposito.
        val estadoPrevio = _uiState.value
        val estado = if (estadoPrevio.tipoCorte == TipoCorte.PARCIAL) {
            val (inicio, fin) = periodoPorDefecto(TipoCorte.PARCIAL, estadoPrevio.historialCortes)
            estadoPrevio.copy(fechaInicio = inicio, fechaFin = fin).also { _uiState.value = it }
        } else {
            estadoPrevio
        }
        viewModelScope.launch {
            val sucursalId = preferences.deviceConfig.first().sucursalIdSeleccionada
            if (sucursalId == null) {
                _uiState.value = _uiState.value.copy(
                    mensajeConfirmacion = "No se pudo calcular el corte: falta sucursal activa",
                )
                return@launch
            }
            try {
                val totales = cajaRepository.calcularTotales(sucursalId, estado.fechaInicio, estado.fechaFin)
                _uiState.value = _uiState.value.copy(
                    calculado = true,
                    totalVentas = totales.totalVentas,
                    totalEfectivo = totales.totalEfectivo,
                    totalTarjeta = totales.totalTarjeta,
                    totalRetiros = totales.totalRetiros,
                    montoContado = "",
                    mensajeConfirmacion = null,
                )
            } catch (e: IOException) {
                _uiState.value = _uiState.value.copy(mensajeConfirmacion = "No se pudo calcular el corte: ${e.message}")
            } catch (e: HttpException) {
                _uiState.value = _uiState.value.copy(mensajeConfirmacion = "No se pudo calcular el corte: ${e.message}")
            }
        }
    }

    fun onMontoContadoChange(valor: String) {
        _uiState.value = _uiState.value.copy(montoContado = valor)
    }

    fun onGuardarClick() {
        val estado = _uiState.value
        if (!estado.calculado) return
        viewModelScope.launch {
            val credenciales = obtenerSucursalYUsuario()
            if (credenciales == null) {
                _uiState.value = _uiState.value.copy(
                    mensajeConfirmacion = "No se pudo guardar el corte: falta sucursal o sesión activa",
                )
                return@launch
            }
            val (sucursalId, usuarioId) = credenciales
            val corte = CorteCaja(
                id = UUID.randomUUID().toString(),
                sucursalId = sucursalId,
                usuarioId = usuarioId,
                tipo = estado.tipoCorte.aTextoDominio(),
                fechaInicio = estado.fechaInicio,
                fechaFin = estado.fechaFin,
                totalVentas = estado.totalVentas,
                totalEfectivo = estado.totalEfectivo,
                totalTarjeta = estado.totalTarjeta,
                totalRetiros = estado.totalRetiros,
                montoEsperado = estado.montoEsperado,
                montoContado = estado.montoContado.toBigDecimalOrNull(),
                diferencia = estado.diferencia,
            )
            try {
                cajaRepository.guardarCorte(corte)
            } catch (e: IOException) {
                _uiState.value = _uiState.value.copy(mensajeConfirmacion = "No se pudo guardar el corte: ${e.message}")
                return@launch
            } catch (e: HttpException) {
                _uiState.value = _uiState.value.copy(mensajeConfirmacion = "No se pudo guardar el corte: ${e.message}")
                return@launch
            }

            val nuevoHistorial = listOf(corte) + _uiState.value.historialCortes
            val (inicio, fin) = periodoPorDefecto(estado.tipoCorte, nuevoHistorial)
            _uiState.value = _uiState.value.copy(
                calculado = false,
                montoContado = "",
                historialCortes = nuevoHistorial,
                fechaInicio = inicio,
                fechaFin = fin,
                mensajeConfirmacion = "Corte guardado",
            )
        }
    }

    fun onRegistrarRetiroClick() {
        _uiState.value = _uiState.value.copy(mostrarDialogoRetiro = true, montoRetiro = "", motivoRetiro = "", errorRetiro = null)
    }

    fun onMontoRetiroChange(valor: String) {
        _uiState.value = _uiState.value.copy(montoRetiro = valor)
    }

    fun onMotivoRetiroChange(valor: String) {
        _uiState.value = _uiState.value.copy(motivoRetiro = valor)
    }

    fun onCancelarRetiroClick() {
        _uiState.value = _uiState.value.copy(mostrarDialogoRetiro = false)
    }

    fun onConfirmarRetiroClick() {
        val estado = _uiState.value
        val monto = estado.montoRetiro.toBigDecimalOrNull()
        if (monto == null || monto <= BigDecimal.ZERO) {
            _uiState.value = estado.copy(errorRetiro = "Ingresa un monto valido")
            return
        }

        viewModelScope.launch {
            val credenciales = obtenerSucursalYUsuario()
            if (credenciales == null) {
                _uiState.value = _uiState.value.copy(
                    mostrarDialogoRetiro = false,
                    mensajeConfirmacion = "No se pudo registrar el retiro: falta sucursal o sesión activa",
                )
                return@launch
            }
            val (sucursalId, usuarioId) = credenciales
            val retiro = RetiroEfectivo(
                id = UUID.randomUUID().toString(),
                sucursalId = sucursalId,
                usuarioId = usuarioId,
                monto = monto,
                motivo = estado.motivoRetiro.ifBlank { null },
                fecha = System.currentTimeMillis(),
            )
            try {
                retiroRepository.registrarRetiro(retiro)
            } catch (e: IOException) {
                _uiState.value = _uiState.value.copy(
                    mostrarDialogoRetiro = false,
                    mensajeConfirmacion = "No se pudo registrar el retiro: ${e.message}",
                )
                return@launch
            } catch (e: HttpException) {
                _uiState.value = _uiState.value.copy(
                    mostrarDialogoRetiro = false,
                    mensajeConfirmacion = "No se pudo registrar el retiro: ${e.message}",
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                mostrarDialogoRetiro = false,
                historialRetiros = listOf(retiro) + _uiState.value.historialRetiros,
                mensajeConfirmacion = "Retiro registrado",
            )
        }
    }

    private suspend fun obtenerSucursalYUsuario(): Pair<String, String>? {
        val sucursalId = preferences.deviceConfig.first().sucursalIdSeleccionada
        val usuarioId = sessionManager.session.value?.username
        return if (sucursalId != null && usuarioId != null) sucursalId to usuarioId else null
    }

    private companion object {
        fun estadoInicial(): CajaUiState {
            val (inicio, fin) = periodoPorDefecto(TipoCorte.PARCIAL, emptyList())
            return CajaUiState(tipoCorte = TipoCorte.PARCIAL, fechaInicio = inicio, fechaFin = fin)
        }

        fun inicioDelDia(): Long = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // El "ultimo corte del dia" sale del historial acumulado en esta
        // misma sesion del ViewModel: CajaRepository no expone una consulta
        // de "ultimo corte" (fuera del alcance de esta Parte, ver checklist
        // "Wiring"). Si no hay ninguno, el fallback es el inicio del dia,
        // no una "primera venta" simulada (PLAN.md Parte 10, "Decisiones
        // abiertas" - caso sin ninguna venta registrada hoy todavia).
        fun periodoPorDefecto(tipo: TipoCorte, historial: List<CorteCaja>): Pair<Long, Long> {
            val inicioHoy = inicioDelDia()
            return when (tipo) {
                TipoCorte.PARCIAL -> {
                    val ultimoCorteHoy = historial.filter { it.fechaFin >= inicioHoy }.maxOfOrNull { it.fechaFin }
                    val inicio = ultimoCorteHoy ?: inicioHoy
                    inicio to System.currentTimeMillis()
                }
                TipoCorte.FINAL -> {
                    val fin = Calendar.getInstance().apply {
                        timeInMillis = inicioHoy
                        set(Calendar.HOUR_OF_DAY, 22)
                    }.timeInMillis
                    inicioHoy to fin
                }
            }
        }
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? =
    if (isBlank()) null else runCatching { BigDecimal(this) }.getOrNull()
