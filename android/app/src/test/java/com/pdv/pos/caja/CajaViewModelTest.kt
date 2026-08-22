package com.pdv.pos.caja

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.auth.Session
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.domain.model.TotalesCorte
import com.pdv.pos.domain.repository.CajaRepository
import com.pdv.pos.domain.repository.RetiroEfectivoRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.math.BigDecimal

// UnconfinedTestDispatcher: mismo motivo que VentaViewModelTest -
// ConfiguracionPreferences hace I/O real de DataStore.
@OptIn(ExperimentalCoroutinesApi::class)
class CajaViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun preferences(tempDir: File): ConfiguracionPreferences {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher + SupervisorJob()),
            produceFile = { File(tempDir, "test.preferences_pb") },
        )
        return ConfiguracionPreferences(dataStore)
    }

    private fun totalesDeEjemplo() = TotalesCorte(
        totalVentas = BigDecimal("1500.00"),
        totalEfectivo = BigDecimal("900.00"),
        totalTarjeta = BigDecimal("600.00"),
        totalRetiros = BigDecimal("100.00"),
        montoEsperado = BigDecimal("800.00"),
    )

    @Test
    fun `onCalcularClick populates totales from the repository`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val cajaRepository = mockk<CajaRepository>()
        coEvery { cajaRepository.calcularTotales("suc-1", any(), any()) } returns totalesDeEjemplo()
        val viewModel = CajaViewModel(cajaRepository, mockk<RetiroEfectivoRepository>(), preferences, SessionManager())

        viewModel.onCalcularClick()

        val estado = viewModel.uiState.value
        assertTrue(estado.calculado)
        assertEquals(BigDecimal("1500.00"), estado.totalVentas)
        assertEquals(BigDecimal("900.00"), estado.totalEfectivo)
        assertEquals(BigDecimal("100.00"), estado.totalRetiros)
        assertEquals(BigDecimal("800.00"), estado.montoEsperado)
    }

    @Test
    fun `onCalcularClick refreshes the fechaFin of a corte parcial on every call`(@TempDir tempDir: File) = runTest(dispatcher) {
        // Regresion: un corte parcial reutilizaba el fechaFin congelado
        // desde la ultima vez que se fijo el periodo, asi que un retiro
        // registrado despues quedaba fuera del rango consultado en el
        // siguiente calculo (encontrado en verificacion needs-device,
        // PLAN.md Parte 10). "Calcular corte" debe recalcular fechaFin a
        // la hora de solicitud en cada click, no solo al cargar/cambiar de tipo.
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val cajaRepository = mockk<CajaRepository>()
        val fechaFinCapturado = mutableListOf<Long>()
        coEvery { cajaRepository.calcularTotales("suc-1", any(), capture(fechaFinCapturado)) } returns totalesDeEjemplo()
        val viewModel = CajaViewModel(cajaRepository, mockk<RetiroEfectivoRepository>(), preferences, SessionManager())

        viewModel.onCalcularClick()
        Thread.sleep(5)
        viewModel.onCalcularClick()

        assertEquals(2, fechaFinCapturado.size)
        assertTrue(fechaFinCapturado[1] > fechaFinCapturado[0])
    }

    @Test
    fun `onCalcularClick keeps the corte final period untouched between calls`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val cajaRepository = mockk<CajaRepository>()
        val fechaInicioCapturado = mutableListOf<Long>()
        val fechaFinCapturado = mutableListOf<Long>()
        coEvery {
            cajaRepository.calcularTotales("suc-1", capture(fechaInicioCapturado), capture(fechaFinCapturado))
        } returns totalesDeEjemplo()
        val viewModel = CajaViewModel(cajaRepository, mockk<RetiroEfectivoRepository>(), preferences, SessionManager())
        viewModel.onTipoCorteChange(TipoCorte.FINAL)

        viewModel.onCalcularClick()
        Thread.sleep(5)
        viewModel.onCalcularClick()

        assertEquals(2, fechaFinCapturado.size)
        assertEquals(fechaInicioCapturado[0], fechaInicioCapturado[1])
        assertEquals(fechaFinCapturado[0], fechaFinCapturado[1])
    }

    @Test
    fun `onCalcularClick shows an error when there is no sucursal seleccionada`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        val cajaRepository = mockk<CajaRepository>()
        val viewModel = CajaViewModel(cajaRepository, mockk<RetiroEfectivoRepository>(), preferences, SessionManager())

        viewModel.onCalcularClick()

        assertFalse(viewModel.uiState.value.calculado)
        coVerify(exactly = 0) { cajaRepository.calcularTotales(any(), any(), any()) }
        assertEquals(
            "No se pudo calcular el corte: falta sucursal activa",
            viewModel.uiState.value.mensajeConfirmacion,
        )
    }

    @Test
    fun `onGuardarClick saves the corte and adds it to historial`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.iniciarSesion(Session("admin", "usuario-1", "rol-1"))
        val cajaRepository = mockk<CajaRepository>()
        coEvery { cajaRepository.calcularTotales("suc-1", any(), any()) } returns totalesDeEjemplo()
        coEvery { cajaRepository.guardarCorte(any()) } returns Unit
        val viewModel = CajaViewModel(cajaRepository, mockk<RetiroEfectivoRepository>(), preferences, sessionManager)
        viewModel.onCalcularClick()
        viewModel.onMontoContadoChange("795.00")

        viewModel.onGuardarClick()

        coVerify {
            cajaRepository.guardarCorte(
                match { it.sucursalId == "suc-1" && it.usuarioId == "admin" && it.montoContado == BigDecimal("795.00") },
            )
        }
        val estado = viewModel.uiState.value
        assertFalse(estado.calculado)
        assertEquals(1, estado.historialCortes.size)
        assertEquals("Corte guardado", estado.mensajeConfirmacion)
    }

    @Test
    fun `onGuardarClick shows an error and does not update historial when the repository fails`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.iniciarSesion(Session("admin", "usuario-1", "rol-1"))
        val cajaRepository = mockk<CajaRepository>()
        coEvery { cajaRepository.calcularTotales("suc-1", any(), any()) } returns totalesDeEjemplo()
        coEvery { cajaRepository.guardarCorte(any()) } throws IOException("sin conexion")
        val viewModel = CajaViewModel(cajaRepository, mockk<RetiroEfectivoRepository>(), preferences, sessionManager)
        viewModel.onCalcularClick()

        viewModel.onGuardarClick()

        val estado = viewModel.uiState.value
        assertTrue(estado.historialCortes.isEmpty())
        assertEquals("No se pudo guardar el corte: sin conexion", estado.mensajeConfirmacion)
    }

    @Test
    fun `onConfirmarRetiroClick registers the retiro and adds it to historial`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.iniciarSesion(Session("admin", "usuario-1", "rol-1"))
        val retiroRepository = mockk<RetiroEfectivoRepository>()
        coEvery { retiroRepository.registrarRetiro(any()) } returns Unit
        val viewModel = CajaViewModel(mockk<CajaRepository>(), retiroRepository, preferences, sessionManager)
        viewModel.onRegistrarRetiroClick()
        viewModel.onMontoRetiroChange("100.00")
        viewModel.onMotivoRetiroChange("Pago a proveedor")

        viewModel.onConfirmarRetiroClick()

        coVerify {
            retiroRepository.registrarRetiro(
                match { it.sucursalId == "suc-1" && it.usuarioId == "admin" && it.monto == BigDecimal("100.00") },
            )
        }
        val estado = viewModel.uiState.value
        assertFalse(estado.mostrarDialogoRetiro)
        assertEquals(1, estado.historialRetiros.size)
        assertEquals("Retiro registrado", estado.mensajeConfirmacion)
    }

    @Test
    fun `onConfirmarRetiroClick shows a validation error for an invalid monto without calling the repository`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        val retiroRepository = mockk<RetiroEfectivoRepository>()
        val viewModel = CajaViewModel(mockk<CajaRepository>(), retiroRepository, preferences, SessionManager())
        viewModel.onRegistrarRetiroClick()
        viewModel.onMontoRetiroChange("0")

        viewModel.onConfirmarRetiroClick()

        coVerify(exactly = 0) { retiroRepository.registrarRetiro(any()) }
        assertTrue(viewModel.uiState.value.mostrarDialogoRetiro)
        assertEquals("Ingresa un monto valido", viewModel.uiState.value.errorRetiro)
    }
}
