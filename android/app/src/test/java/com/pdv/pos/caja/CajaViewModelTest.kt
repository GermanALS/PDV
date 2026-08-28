package com.pdv.pos.caja

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.auth.Session
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.caja.export.CajaExportManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.domain.model.CorteCaja
import com.pdv.pos.domain.model.RetiroEfectivo
import com.pdv.pos.domain.model.TotalesCorte
import com.pdv.pos.domain.repository.CajaRepository
import com.pdv.pos.domain.repository.RetiroEfectivoRepository
import com.pdv.pos.inventario.export.ArchivoExportado
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
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

    // PLAN.md Parte 18, sub-parte F: el ViewModel ahora observa el
    // historial en vez de mutarlo el mismo - todo mock de CajaRepository/
    // RetiroEfectivoRepository necesita observeCortes/observeRetiros
    // stubeado (el init{} del ViewModel se suscribe siempre), aunque el
    // test no verifique el historial en si.
    private fun cajaRepository(historial: MutableStateFlow<List<CorteCaja>> = MutableStateFlow(emptyList())): CajaRepository {
        val repository = mockk<CajaRepository>()
        every { repository.observeCortes(any()) } returns historial
        return repository
    }

    private fun retiroRepository(historial: MutableStateFlow<List<RetiroEfectivo>> = MutableStateFlow(emptyList())): RetiroEfectivoRepository {
        val repository = mockk<RetiroEfectivoRepository>()
        every { repository.observeRetiros(any()) } returns historial
        return repository
    }

    private fun totalesDeEjemplo() = TotalesCorte(
        totalVentas = BigDecimal("1500.00"),
        totalEfectivo = BigDecimal("900.00"),
        totalTarjeta = BigDecimal("600.00"),
        totalRetiros = BigDecimal("100.00"),
        montoEsperado = BigDecimal("800.00"),
    )

    // fechaFin reciente: el historial de CajaViewModel se filtra a los
    // ultimos 7 dias (PLAN.md Parte 18, sub-parte I).
    private fun corteDeEjemplo() = CorteCaja(
        id = "corte-1",
        sucursalId = "suc-1",
        usuarioId = "german",
        tipo = "parcial",
        fechaInicio = System.currentTimeMillis() - 3_600_000L,
        fechaFin = System.currentTimeMillis(),
        totalVentas = BigDecimal("100.00"),
        totalEfectivo = BigDecimal("100.00"),
        totalTarjeta = BigDecimal.ZERO,
        totalRetiros = BigDecimal.ZERO,
        montoEsperado = BigDecimal("100.00"),
        montoContado = null,
        diferencia = null,
    )

    @Test
    fun `onCalcularClick populates totales from the repository`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val cajaRepository = cajaRepository()
        coEvery { cajaRepository.calcularTotales("suc-1", any(), any()) } returns totalesDeEjemplo()
        val viewModel = CajaViewModel(cajaRepository, retiroRepository(), preferences, SessionManager(), CajaRefreshSignal(), mockk(relaxed = true))

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
        val cajaRepository = cajaRepository()
        val fechaFinCapturado = mutableListOf<Long>()
        coEvery { cajaRepository.calcularTotales("suc-1", any(), capture(fechaFinCapturado)) } returns totalesDeEjemplo()
        val viewModel = CajaViewModel(cajaRepository, retiroRepository(), preferences, SessionManager(), CajaRefreshSignal(), mockk(relaxed = true))

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
        val cajaRepository = cajaRepository()
        val fechaInicioCapturado = mutableListOf<Long>()
        val fechaFinCapturado = mutableListOf<Long>()
        coEvery {
            cajaRepository.calcularTotales("suc-1", capture(fechaInicioCapturado), capture(fechaFinCapturado))
        } returns totalesDeEjemplo()
        val viewModel = CajaViewModel(cajaRepository, retiroRepository(), preferences, SessionManager(), CajaRefreshSignal(), mockk(relaxed = true))
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
        val cajaRepository = cajaRepository()
        val viewModel = CajaViewModel(cajaRepository, retiroRepository(), preferences, SessionManager(), CajaRefreshSignal(), mockk(relaxed = true))

        viewModel.onCalcularClick()

        assertFalse(viewModel.uiState.value.calculado)
        coVerify(exactly = 0) { cajaRepository.calcularTotales(any(), any(), any()) }
        assertEquals(
            "No se pudo calcular el corte: falta sucursal activa",
            viewModel.uiState.value.mensajeConfirmacion,
        )
    }

    @Test
    fun `onGuardarClick saves the corte and the reactive historial reflects it`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.iniciarSesion(Session("admin", "usuario-1", "rol-1"))
        val historialCortes = MutableStateFlow<List<CorteCaja>>(emptyList())
        val cajaRepository = cajaRepository(historialCortes)
        coEvery { cajaRepository.calcularTotales("suc-1", any(), any()) } returns totalesDeEjemplo()
        // Simula lo que Room hace en LOCAL al invalidar el Flow tras el
        // insert real (LocalCajaRepository/CajaDao.observarCortes): el mock
        // no tiene Room detras, asi que el "refresco" se simula empujando el
        // nuevo valor al mismo MutableStateFlow que observa el ViewModel.
        coEvery { cajaRepository.guardarCorte(any()) } answers {
            val corte = firstArg<CorteCaja>()
            historialCortes.value = listOf(corte) + historialCortes.value
        }
        val viewModel = CajaViewModel(cajaRepository, retiroRepository(), preferences, sessionManager, CajaRefreshSignal(), mockk(relaxed = true))
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
        val cajaRepository = cajaRepository()
        coEvery { cajaRepository.calcularTotales("suc-1", any(), any()) } returns totalesDeEjemplo()
        coEvery { cajaRepository.guardarCorte(any()) } throws IOException("sin conexion")
        val viewModel = CajaViewModel(cajaRepository, retiroRepository(), preferences, sessionManager, CajaRefreshSignal(), mockk(relaxed = true))
        viewModel.onCalcularClick()

        viewModel.onGuardarClick()

        val estado = viewModel.uiState.value
        assertTrue(estado.historialCortes.isEmpty())
        assertEquals("No se pudo guardar el corte: sin conexion", estado.mensajeConfirmacion)
    }

    @Test
    fun `onConfirmarRetiroClick registers the retiro and the reactive historial reflects it`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.iniciarSesion(Session("admin", "usuario-1", "rol-1"))
        val historialRetiros = MutableStateFlow<List<RetiroEfectivo>>(emptyList())
        val retiroRepository = retiroRepository(historialRetiros)
        coEvery { retiroRepository.registrarRetiro(any()) } answers {
            val retiro = firstArg<RetiroEfectivo>()
            historialRetiros.value = listOf(retiro) + historialRetiros.value
        }
        val viewModel = CajaViewModel(cajaRepository(), retiroRepository, preferences, sessionManager, CajaRefreshSignal(), mockk(relaxed = true))
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
        val retiroRepository = retiroRepository()
        val viewModel = CajaViewModel(cajaRepository(), retiroRepository, preferences, SessionManager(), CajaRefreshSignal(), mockk(relaxed = true))
        viewModel.onRegistrarRetiroClick()
        viewModel.onMontoRetiroChange("0")

        viewModel.onConfirmarRetiroClick()

        coVerify(exactly = 0) { retiroRepository.registrarRetiro(any()) }
        assertTrue(viewModel.uiState.value.mostrarDialogoRetiro)
        assertEquals("Ingresa un monto valido", viewModel.uiState.value.errorRetiro)
    }

    // Cierra el gap encontrado en pruebas de la Parte 16 (PLAN.md Parte 18,
    // sub-parte F): un corte/retiro registrado desde OTRO lugar que
    // comparte el mismo CajaRepository/RetiroEfectivoRepository (ej.
    // EjecutorAccionesIa via la IA) debe aparecer sin que CajaViewModel
    // llame a ningun metodo propio - solo por estar suscrito al mismo Flow.
    @Test
    fun `un corte registrado desde otro lugar (ej la IA) aparece en el historial sin llamar a ningun metodo del ViewModel`(
        @TempDir tempDir: File,
    ) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val historialCortes = MutableStateFlow<List<CorteCaja>>(emptyList())
        val viewModel = CajaViewModel(
            cajaRepository(historialCortes),
            retiroRepository(),
            preferences,
            SessionManager(),
            CajaRefreshSignal(),
            mockk(relaxed = true),
        )
        assertTrue(viewModel.uiState.value.historialCortes.isEmpty())

        // Simula la invalidacion reactiva que Room dispara automaticamente
        // en LOCAL cuando otro llamador (ej. EjecutorAccionesIa) inserta un
        // corte via el mismo repositorio.
        historialCortes.value = listOf(corteDeEjemplo())

        assertEquals(1, viewModel.uiState.value.historialCortes.size)
    }

    @Test
    fun `el historial visible descarta los cortes de mas de 7 dias`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val historialCortes = MutableStateFlow<List<CorteCaja>>(emptyList())
        val viewModel = CajaViewModel(
            cajaRepository(historialCortes),
            retiroRepository(),
            preferences,
            SessionManager(),
            CajaRefreshSignal(),
            mockk(relaxed = true),
        )

        val corteViejo = corteDeEjemplo().copy(id = "viejo", fechaFin = System.currentTimeMillis() - 10L * 24 * 60 * 60 * 1000)
        historialCortes.value = listOf(corteDeEjemplo(), corteViejo)

        assertEquals(listOf("corte-1"), viewModel.uiState.value.historialCortes.map { it.id })
    }

    @Test
    fun `onConfirmarExportarClick trae el periodo, genera el csv y lo expone para compartir`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val cajaRepository = cajaRepository()
        val retiroRepository = retiroRepository()
        val cortesDelPeriodo = listOf(corteDeEjemplo())
        coEvery { cajaRepository.obtenerCortesDelPeriodo("suc-1", any(), any()) } returns cortesDelPeriodo
        coEvery { retiroRepository.obtenerRetirosDelPeriodo("suc-1", any(), any()) } returns emptyList()
        val exportManager = mockk<CajaExportManager>()
        val archivo = mockk<ArchivoExportado>()
        coEvery { exportManager.exportarCortesYRetiros(any(), any()) } returns archivo
        val viewModel = CajaViewModel(
            cajaRepository,
            retiroRepository,
            preferences,
            SessionManager(),
            CajaRefreshSignal(),
            exportManager,
        )

        viewModel.onExportarClick()
        assertTrue(viewModel.uiState.value.mostrarDialogoExportar)
        viewModel.onConfirmarExportarClick()

        coVerify { cajaRepository.obtenerCortesDelPeriodo("suc-1", any(), any()) }
        coVerify { retiroRepository.obtenerRetirosDelPeriodo("suc-1", any(), any()) }
        coVerify { exportManager.exportarCortesYRetiros(cortesDelPeriodo, emptyList()) }
        val estado = viewModel.uiState.value
        assertEquals(archivo, estado.archivoExportado)
        assertFalse(estado.mostrarDialogoExportar)
        assertFalse(estado.exportando)
    }
}
