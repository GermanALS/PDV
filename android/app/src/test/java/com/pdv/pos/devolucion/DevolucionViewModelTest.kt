package com.pdv.pos.devolucion

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.domain.repository.DevolucionRepository
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
class DevolucionViewModelTest {

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

    private fun viewModelConUnaLinea(
        devolucionRepository: DevolucionRepository,
        preferences: ConfiguracionPreferences,
        sessionManager: SessionManager,
    ): DevolucionViewModel {
        val viewModel = DevolucionViewModel(devolucionRepository, preferences, sessionManager)
        viewModel.onBusquedaChange("REF-001")
        viewModel.buscar()
        viewModel.agregarLinea()
        return viewModel
    }

    @Test
    fun `registrarDevolucion registra la devolucion, limpia las lineas y actualiza el historial`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.login("admin", "password")
        val devolucionRepository = mockk<DevolucionRepository>()
        coEvery { devolucionRepository.registrarDevolucion(any()) } returns Unit
        val viewModel = viewModelConUnaLinea(devolucionRepository, preferences, sessionManager)

        viewModel.registrarDevolucion()

        coVerify {
            devolucionRepository.registrarDevolucion(
                match { it.sucursalId == "suc-1" && it.usuarioId == "admin" && it.lineas.size == 1 },
            )
        }
        assertTrue(viewModel.uiState.value.lineas.isEmpty())
        assertEquals(1, viewModel.uiState.value.historial.size)
        assertTrue(viewModel.uiState.value.mensajeConfirmacion!!.startsWith("Devolución registrada"))
    }

    @Test
    fun `registrarDevolucion muestra un error y conserva las lineas cuando falta sucursal o sesion`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        val sessionManager = SessionManager()
        val devolucionRepository = mockk<DevolucionRepository>()
        val viewModel = viewModelConUnaLinea(devolucionRepository, preferences, sessionManager)

        viewModel.registrarDevolucion()

        coVerify(exactly = 0) { devolucionRepository.registrarDevolucion(any()) }
        assertEquals(1, viewModel.uiState.value.lineas.size)
        assertEquals(
            "No se pudo registrar la devolución: falta sucursal o sesión activa",
            viewModel.uiState.value.mensajeConfirmacion,
        )
    }

    @Test
    fun `registrarDevolucion muestra un error y conserva las lineas cuando el repositorio falla`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.login("admin", "password")
        val devolucionRepository = mockk<DevolucionRepository>()
        coEvery { devolucionRepository.registrarDevolucion(any()) } throws IOException("sin conexion")
        val viewModel = viewModelConUnaLinea(devolucionRepository, preferences, sessionManager)

        viewModel.registrarDevolucion()

        assertEquals(1, viewModel.uiState.value.lineas.size)
        assertTrue(viewModel.uiState.value.historial.isEmpty())
        assertEquals("No se pudo registrar la devolución: sin conexion", viewModel.uiState.value.mensajeConfirmacion)
    }
}
