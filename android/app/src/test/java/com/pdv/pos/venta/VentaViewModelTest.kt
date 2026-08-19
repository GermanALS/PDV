package com.pdv.pos.venta

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.domain.repository.VentaRepository
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

// UnconfinedTestDispatcher: mismo motivo que ConfiguracionViewModelTest -
// ConfiguracionPreferences hace I/O real de DataStore.
@OptIn(ExperimentalCoroutinesApi::class)
class VentaViewModelTest {

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

    private fun viewModelConUnArticuloEnElCarrito(
        ventaRepository: VentaRepository,
        preferences: ConfiguracionPreferences,
        sessionManager: SessionManager,
    ): VentaViewModel {
        val viewModel = VentaViewModel(ventaRepository, preferences, sessionManager)
        viewModel.onBusquedaChange("REF-001")
        viewModel.buscar()
        viewModel.agregarAlCarrito()
        return viewModel
    }

    @Test
    fun `confirmarVenta persists the venta via VentaRepository and clears the cart`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.login("admin", "password")
        val ventaRepository = mockk<VentaRepository>()
        coEvery { ventaRepository.registrarVenta(any()) } returns Unit
        val viewModel = viewModelConUnArticuloEnElCarrito(ventaRepository, preferences, sessionManager)

        viewModel.confirmarVenta()

        coVerify {
            ventaRepository.registrarVenta(
                match { it.sucursalId == "suc-1" && it.usuarioId == "admin" && it.lineas.size == 1 },
            )
        }
        assertTrue(viewModel.uiState.value.carrito.isEmpty())
        assertTrue(viewModel.uiState.value.mensajeConfirmacion!!.startsWith("Venta registrada"))
    }

    @Test
    fun `confirmarVenta shows an error and keeps the cart when there is no sucursal or active session`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        val sessionManager = SessionManager()
        val ventaRepository = mockk<VentaRepository>()
        val viewModel = viewModelConUnArticuloEnElCarrito(ventaRepository, preferences, sessionManager)

        viewModel.confirmarVenta()

        coVerify(exactly = 0) { ventaRepository.registrarVenta(any()) }
        assertEquals(1, viewModel.uiState.value.carrito.size)
        assertEquals(
            "No se pudo registrar la venta: falta sucursal o sesión activa",
            viewModel.uiState.value.mensajeConfirmacion,
        )
    }

    @Test
    fun `confirmarVenta shows an error and keeps the cart when the repository fails`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.login("admin", "password")
        val ventaRepository = mockk<VentaRepository>()
        coEvery { ventaRepository.registrarVenta(any()) } throws IOException("sin conexion")
        val viewModel = viewModelConUnArticuloEnElCarrito(ventaRepository, preferences, sessionManager)

        viewModel.confirmarVenta()

        assertEquals(1, viewModel.uiState.value.carrito.size)
        assertEquals("No se pudo registrar la venta: sin conexion", viewModel.uiState.value.mensajeConfirmacion)
    }
}
