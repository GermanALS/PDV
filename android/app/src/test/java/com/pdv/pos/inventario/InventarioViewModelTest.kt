package com.pdv.pos.inventario

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.domain.model.Articulo
import com.pdv.pos.domain.model.InventarioItem
import com.pdv.pos.domain.model.PaginaInventario
import com.pdv.pos.domain.repository.InventarioRepository
import com.pdv.pos.inventario.export.InventarioExportManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.math.BigDecimal

// UnconfinedTestDispatcher: mismo motivo que EntradaViewModelTest -
// ConfiguracionPreferences hace I/O real de DataStore, y el init de
// InventarioViewModel arranca varios collect sobre Flows del repositorio.
@OptIn(ExperimentalCoroutinesApi::class)
class InventarioViewModelTest {

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

    private fun itemDeEjemplo() = InventarioItem(
        articulo = Articulo(
            id = "art-1",
            sku = "REF-001",
            nombre = "Refresco de cola 600ml",
            unidadMedida = "pieza",
            precioVenta = BigDecimal("18.50"),
        ),
        cantidad = BigDecimal("24"),
        ubicacion = "Estante A1",
    )

    private fun inventarioRepositoryConDatosVacios(): InventarioRepository {
        val repository = mockk<InventarioRepository>()
        every { repository.observarInventario(any(), any(), any(), any()) } returns
            flowOf(PaginaInventario(items = emptyList(), pagina = 1, tamanioPagina = 20, total = 0))
        every { repository.observarCategorias() } returns flowOf(emptyList())
        every { repository.observarUnidadesMedida() } returns flowOf(emptyList())
        every { repository.observarUbicaciones(any()) } returns flowOf(emptyList())
        return repository
    }

    private fun viewModel(
        inventarioRepository: InventarioRepository,
        preferences: ConfiguracionPreferences,
        sessionManager: SessionManager,
    ) = InventarioViewModel(inventarioRepository, preferences, sessionManager, mockk<InventarioExportManager>())

    @Test
    fun `guardarCambios persists via InventarioRepository and shows confirmation`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.login("admin", "password")
        val inventarioRepository = inventarioRepositoryConDatosVacios()
        coEvery { inventarioRepository.actualizarArticulo(any()) } returns Unit
        val viewModel = viewModel(inventarioRepository, preferences, sessionManager)

        viewModel.onEditarClick(itemDeEjemplo())
        viewModel.onEdicionPrecioVentaChange("19.00")
        viewModel.onEdicionCantidadChange("30")

        viewModel.guardarCambios()

        coVerify {
            inventarioRepository.actualizarArticulo(
                match {
                    it.articuloId == "art-1" &&
                        it.sucursalId == "suc-1" &&
                        it.usuarioId == "admin" &&
                        it.precioVenta == BigDecimal("19.00") &&
                        it.nuevaCantidad == BigDecimal("30")
                },
            )
        }
        assertTrue(viewModel.uiState.value.mensajeConfirmacion!!.startsWith("Artículo actualizado"))
        assertEquals(null, viewModel.uiState.value.edicion)
    }

    @Test
    fun `guardarCambios shows an error when there is no sucursal or active session`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        val sessionManager = SessionManager()
        val inventarioRepository = inventarioRepositoryConDatosVacios()
        val viewModel = viewModel(inventarioRepository, preferences, sessionManager)

        viewModel.onEditarClick(itemDeEjemplo())
        viewModel.guardarCambios()

        coVerify(exactly = 0) { inventarioRepository.actualizarArticulo(any()) }
        assertEquals(
            "No se pudo actualizar: falta sucursal o sesión activa",
            viewModel.uiState.value.mensajeConfirmacion,
        )
    }

    @Test
    fun `guardarCambios shows a validation error when cantidad is not a valid number`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.login("admin", "password")
        val inventarioRepository = inventarioRepositoryConDatosVacios()
        val viewModel = viewModel(inventarioRepository, preferences, sessionManager)

        viewModel.onEditarClick(itemDeEjemplo())
        viewModel.onEdicionCantidadChange("no-numero")
        viewModel.guardarCambios()

        coVerify(exactly = 0) { inventarioRepository.actualizarArticulo(any()) }
        assertEquals("La cantidad debe ser un número válido", viewModel.uiState.value.mensajeConfirmacion)
    }

    @Test
    fun `guardarCambios shows an error and keeps the dialog open when the repository fails`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.login("admin", "password")
        val inventarioRepository = inventarioRepositoryConDatosVacios()
        coEvery { inventarioRepository.actualizarArticulo(any()) } throws IOException("sin conexion")
        val viewModel = viewModel(inventarioRepository, preferences, sessionManager)

        viewModel.onEditarClick(itemDeEjemplo())
        viewModel.guardarCambios()

        assertEquals("No se pudo actualizar el artículo: sin conexion", viewModel.uiState.value.mensajeConfirmacion)
        assertNotNull(viewModel.uiState.value.edicion)
    }
}
