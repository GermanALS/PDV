package com.pdv.pos.entrada

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.domain.repository.EntradaRepository
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

// UnconfinedTestDispatcher: mismo motivo que VentaViewModelTest -
// ConfiguracionPreferences hace I/O real de DataStore.
@OptIn(ExperimentalCoroutinesApi::class)
class EntradaViewModelTest {

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

    private fun viewModel(
        entradaRepository: EntradaRepository,
        preferences: ConfiguracionPreferences,
        sessionManager: SessionManager,
    ) = EntradaViewModel(entradaRepository, preferences, sessionManager)

    @Test
    fun `registrarEntrada de articulo existente persists via EntradaRepository and shows confirmation`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.login("admin", "password")
        val entradaRepository = mockk<EntradaRepository>()
        coEvery { entradaRepository.registrarEntrada(any()) } returns Unit
        val viewModel = viewModel(entradaRepository, preferences, sessionManager)
        viewModel.onBusquedaChange("REF-001")
        viewModel.buscarArticuloExistente()
        viewModel.onCantidadChange("5")
        viewModel.onUbicacionChange("Estante A1")

        viewModel.registrarEntrada()

        coVerify {
            entradaRepository.registrarEntrada(
                match {
                    it is com.pdv.pos.domain.model.Entrada.DeArticuloExistente &&
                        it.sucursalId == "suc-1" &&
                        it.usuarioId == "admin" &&
                        it.articuloId == "11111111-1111-4111-8111-111111111111"
                },
            )
        }
        assertTrue(viewModel.uiState.value.mensajeConfirmacion!!.startsWith("Entrada registrada"))
    }

    @Test
    fun `registrarEntrada de articulo nuevo builds ArticuloNuevo and persists via EntradaRepository`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.login("admin", "password")
        val entradaRepository = mockk<EntradaRepository>()
        coEvery { entradaRepository.registrarEntrada(any()) } returns Unit
        val viewModel = viewModel(entradaRepository, preferences, sessionManager)
        viewModel.onTipoChange(TipoEntrada.ARTICULO_NUEVO)
        viewModel.onSkuChange("NEW-001")
        viewModel.onNombreChange("Articulo nuevo")
        viewModel.onUnidadMedidaChange("pieza")
        viewModel.onPrecioVentaChange("15.00")
        viewModel.onCantidadChange("10")

        viewModel.registrarEntrada()

        coVerify {
            entradaRepository.registrarEntrada(
                match {
                    it is com.pdv.pos.domain.model.Entrada.DeArticuloNuevo &&
                        it.articulo.sku == "NEW-001" &&
                        it.articulo.nombre == "Articulo nuevo" &&
                        it.cantidad == java.math.BigDecimal("10")
                },
            )
        }
    }

    @Test
    fun `registrarEntrada shows an error when there is no sucursal or active session`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        val sessionManager = SessionManager()
        val entradaRepository = mockk<EntradaRepository>()
        val viewModel = viewModel(entradaRepository, preferences, sessionManager)
        viewModel.onBusquedaChange("REF-001")
        viewModel.buscarArticuloExistente()
        viewModel.onCantidadChange("5")

        viewModel.registrarEntrada()

        coVerify(exactly = 0) { entradaRepository.registrarEntrada(any()) }
        assertEquals(
            "No se pudo registrar la entrada: falta sucursal o sesión activa",
            viewModel.uiState.value.mensajeConfirmacion,
        )
    }

    @Test
    fun `registrarEntrada shows a validation error and does not call the repository when cantidad is missing`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.login("admin", "password")
        val entradaRepository = mockk<EntradaRepository>()
        val viewModel = viewModel(entradaRepository, preferences, sessionManager)
        viewModel.onBusquedaChange("REF-001")
        viewModel.buscarArticuloExistente()

        viewModel.registrarEntrada()

        coVerify(exactly = 0) { entradaRepository.registrarEntrada(any()) }
        assertEquals("La cantidad debe ser un número mayor a 0", viewModel.uiState.value.mensajeConfirmacion)
    }

    @Test
    fun `registrarEntrada shows an error and keeps the form when the repository fails`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.login("admin", "password")
        val entradaRepository = mockk<EntradaRepository>()
        coEvery { entradaRepository.registrarEntrada(any()) } throws IOException("sin conexion")
        val viewModel = viewModel(entradaRepository, preferences, sessionManager)
        viewModel.onBusquedaChange("REF-001")
        viewModel.buscarArticuloExistente()
        viewModel.onCantidadChange("5")

        viewModel.registrarEntrada()

        assertEquals("No se pudo registrar la entrada: sin conexion", viewModel.uiState.value.mensajeConfirmacion)
        assertEquals("5", viewModel.uiState.value.cantidad)
    }
}
