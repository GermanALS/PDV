package com.pdv.pos.devolucion

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.auth.Session
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.domain.model.Articulo
import com.pdv.pos.domain.model.InventarioItem
import com.pdv.pos.domain.model.PaginaInventario
import com.pdv.pos.domain.repository.DevolucionRepository
import com.pdv.pos.domain.repository.InventarioRepository
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
import org.junit.jupiter.api.Assertions.assertNull
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

    private fun articuloDeEjemplo() = Articulo(
        id = "11111111-1111-4111-8111-111111111111",
        codigoBarras = "7501234567890",
        sku = "REF-001",
        nombre = "Refresco de cola 600ml",
        unidadMedida = "pieza",
        precioVenta = BigDecimal("18.50"),
    )

    // Mismo patron que VentaViewModelTest: filtra por nombre/sku/codigoBarras
    // como el repositorio real, para ejercer el mismo contrato que
    // InventarioDao.observarPagina.
    private fun inventarioRepository(articulos: List<Articulo> = listOf(articuloDeEjemplo())): InventarioRepository {
        val repo = mockk<InventarioRepository>()
        every { repo.observarInventario(any(), any(), any(), any()) } answers {
            val termino = it.invocation.args[1] as String
            val encontrados = articulos.filter { articulo ->
                articulo.nombre.contains(termino, ignoreCase = true) ||
                    articulo.sku.contains(termino, ignoreCase = true) ||
                    (articulo.codigoBarras?.contains(termino, ignoreCase = true) == true)
            }
            flowOf(
                PaginaInventario(
                    items = encontrados.map { articulo -> InventarioItem(articulo, BigDecimal.ZERO, null) },
                    pagina = 1,
                    tamanioPagina = 1,
                    total = encontrados.size,
                ),
            )
        }
        return repo
    }

    private fun viewModelConUnaLinea(
        devolucionRepository: DevolucionRepository,
        preferences: ConfiguracionPreferences,
        sessionManager: SessionManager,
        inventarioRepository: InventarioRepository = inventarioRepository(),
    ): DevolucionViewModel {
        val viewModel = DevolucionViewModel(devolucionRepository, inventarioRepository, preferences, sessionManager)
        viewModel.onBusquedaChange("REF-001")
        viewModel.buscar()
        viewModel.agregarLinea()
        return viewModel
    }

    @Test
    fun `buscar encuentra un articulo real del inventario, no un catalogo de ejemplo`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val viewModel = DevolucionViewModel(mockk(), inventarioRepository(), preferences, SessionManager())

        viewModel.onBusquedaChange("REF-001")
        viewModel.buscar()

        assertEquals(articuloDeEjemplo(), viewModel.uiState.value.articuloEncontrado)
        assertNull(viewModel.uiState.value.errorBusqueda)
    }

    @Test
    fun `buscar sin sucursal seleccionada pide elegir una en Configuracion`(@TempDir tempDir: File) = runTest(dispatcher) {
        val viewModel = DevolucionViewModel(mockk(), inventarioRepository(), preferences(tempDir), SessionManager())

        viewModel.onBusquedaChange("REF-001")
        viewModel.buscar()

        assertNull(viewModel.uiState.value.articuloEncontrado)
        assertEquals("Selecciona una sucursal en Configuración", viewModel.uiState.value.errorBusqueda)
    }

    @Test
    fun `registrarDevolucion registra la devolucion, limpia las lineas y actualiza el historial`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.iniciarSesion(Session("admin", "usuario-1", "rol-1"))
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
        // Sucursal seleccionada para que buscar()/agregarLinea() funcionen;
        // lo que falta para registrarDevolucion es la sesion.
        preferences.setSucursalSeleccionada("suc-1")
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
        sessionManager.iniciarSesion(Session("admin", "usuario-1", "rol-1"))
        val devolucionRepository = mockk<DevolucionRepository>()
        coEvery { devolucionRepository.registrarDevolucion(any()) } throws IOException("sin conexion")
        val viewModel = viewModelConUnaLinea(devolucionRepository, preferences, sessionManager)

        viewModel.registrarDevolucion()

        assertEquals(1, viewModel.uiState.value.lineas.size)
        assertTrue(viewModel.uiState.value.historial.isEmpty())
        assertEquals("No se pudo registrar la devolución: sin conexion", viewModel.uiState.value.mensajeConfirmacion)
    }
}
