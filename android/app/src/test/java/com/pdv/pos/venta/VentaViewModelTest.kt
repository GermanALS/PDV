package com.pdv.pos.venta

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.auth.Session
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.domain.model.Articulo
import com.pdv.pos.domain.model.InventarioItem
import com.pdv.pos.domain.model.PaginaInventario
import com.pdv.pos.domain.model.Sucursal
import com.pdv.pos.domain.repository.InventarioRepository
import com.pdv.pos.domain.repository.SucursalRepository
import com.pdv.pos.domain.repository.VentaRepository
import com.pdv.pos.venta.ticket.TicketManager
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.math.BigDecimal

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

    private fun sucursalRepository(): SucursalRepository {
        val repository = mockk<SucursalRepository>()
        every { repository.observeSucursales() } returns flowOf(
            listOf(Sucursal(id = "suc-1", nombre = "Sucursal Test")),
        )
        return repository
    }

    private fun ticketManager(): TicketManager {
        val manager = mockk<TicketManager>()
        coEvery { manager.generarTicket(any(), any(), any()) } returns File("ticket-test.pdf")
        return manager
    }

    private fun articuloDeEjemplo() = Articulo(
        id = "11111111-1111-4111-8111-111111111111",
        codigoBarras = "7501234567890",
        sku = "REF-001",
        nombre = "Refresco de cola 600ml",
        unidadMedida = "pieza",
        precioVenta = BigDecimal("18.50"),
    )

    // Filtra por nombre/sku/codigoBarras, mismo criterio de matching que
    // InventarioDao.observarPagina (LIKE), para que las pruebas ejerzan el
    // mismo contrato que el repositorio real.
    private fun inventarioRepository(articulos: List<Articulo> = listOf(articuloDeEjemplo())): InventarioRepository {
        val repo = mockk<InventarioRepository>()
        every { repo.observarInventario(any(), any(), any(), any()) } answers {
            val termino = (it.invocation.args[1] as String)
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

    private fun viewModelConUnArticuloEnElCarrito(
        ventaRepository: VentaRepository,
        preferences: ConfiguracionPreferences,
        sessionManager: SessionManager,
        sucursalRepository: SucursalRepository = sucursalRepository(),
        ticketManager: TicketManager = ticketManager(),
        inventarioRepository: InventarioRepository = inventarioRepository(),
    ): VentaViewModel {
        val viewModel = VentaViewModel(
            ventaRepository,
            inventarioRepository,
            preferences,
            sessionManager,
            sucursalRepository,
            ticketManager,
        )
        viewModel.onBusquedaChange("REF-001")
        viewModel.buscar()
        viewModel.agregarAlCarrito()
        return viewModel
    }

    @Test
    fun `confirmarVenta con efectivo pide el monto recibido y calcula el cambio al confirmar`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.iniciarSesion(Session("admin", "usuario-1", "rol-1"))
        val ventaRepository = mockk<VentaRepository>()
        coEvery { ventaRepository.registrarVenta(any()) } returns Unit
        val viewModel = viewModelConUnArticuloEnElCarrito(ventaRepository, preferences, sessionManager)

        viewModel.confirmarVenta()
        assertTrue(viewModel.uiState.value.mostrarDialogoEfectivo)
        coVerify(exactly = 0) { ventaRepository.registrarVenta(any()) }

        viewModel.onEfectivoIngresadoChange("50.00")
        viewModel.confirmarEfectivo()

        coVerify {
            ventaRepository.registrarVenta(
                match { it.sucursalId == "suc-1" && it.usuarioId == "admin" && it.lineas.size == 1 },
            )
        }
        assertTrue(viewModel.uiState.value.carrito.isEmpty())
        assertFalse(viewModel.uiState.value.mostrarDialogoEfectivo)
        assertTrue(viewModel.uiState.value.mensajeConfirmacion!!.startsWith("Venta registrada"))
        assertEquals(BigDecimal("31.50"), viewModel.uiState.value.cambioEntregado)
    }

    @Test
    fun `confirmarEfectivo con monto insuficiente muestra un error y no registra la venta`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.iniciarSesion(Session("admin", "usuario-1", "rol-1"))
        val ventaRepository = mockk<VentaRepository>()
        val viewModel = viewModelConUnArticuloEnElCarrito(ventaRepository, preferences, sessionManager)

        viewModel.confirmarVenta()
        viewModel.onEfectivoIngresadoChange("5.00")
        viewModel.confirmarEfectivo()

        coVerify(exactly = 0) { ventaRepository.registrarVenta(any()) }
        assertTrue(viewModel.uiState.value.mostrarDialogoEfectivo)
        assertEquals(1, viewModel.uiState.value.carrito.size)
        assertTrue(viewModel.uiState.value.errorEfectivo != null)
    }

    @Test
    fun `confirmarVenta con tarjeta registra directamente sin abrir el dialogo de efectivo`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.iniciarSesion(Session("admin", "usuario-1", "rol-1"))
        val ventaRepository = mockk<VentaRepository>()
        coEvery { ventaRepository.registrarVenta(any()) } returns Unit
        val viewModel = viewModelConUnArticuloEnElCarrito(ventaRepository, preferences, sessionManager)
        viewModel.onMetodoPagoSelected(MetodoPago.TARJETA)

        viewModel.confirmarVenta()

        assertFalse(viewModel.uiState.value.mostrarDialogoEfectivo)
        coVerify { ventaRepository.registrarVenta(any()) }
        assertNull(viewModel.uiState.value.cambioEntregado)
        assertTrue(viewModel.uiState.value.mensajeConfirmacion!!.startsWith("Venta registrada"))
    }

    @Test
    fun `confirmarVenta shows an error and keeps the cart when there is no active session`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        // Sucursal seleccionada para que la busqueda (ahora real, contra
        // InventarioRepository) pueda poblar el carrito - lo que este test
        // ejercita es la falta de sesion activa al confirmar, no la falta
        // de sucursal.
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        val ventaRepository = mockk<VentaRepository>()
        val viewModel = viewModelConUnArticuloEnElCarrito(ventaRepository, preferences, sessionManager)

        viewModel.confirmarVenta()

        coVerify(exactly = 0) { ventaRepository.registrarVenta(any()) }
        assertFalse(viewModel.uiState.value.mostrarDialogoEfectivo)
        assertEquals(1, viewModel.uiState.value.carrito.size)
        assertEquals(
            "No se pudo registrar la venta: falta sucursal o sesión activa",
            viewModel.uiState.value.mensajeConfirmacion,
        )
    }

    @Test
    fun `confirmarEfectivo shows an error and keeps the cart when the repository fails`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.iniciarSesion(Session("admin", "usuario-1", "rol-1"))
        val ventaRepository = mockk<VentaRepository>()
        coEvery { ventaRepository.registrarVenta(any()) } throws IOException("sin conexion")
        val viewModel = viewModelConUnArticuloEnElCarrito(ventaRepository, preferences, sessionManager)

        viewModel.confirmarVenta()
        viewModel.onEfectivoIngresadoChange("50.00")
        viewModel.confirmarEfectivo()

        assertEquals(1, viewModel.uiState.value.carrito.size)
        assertFalse(viewModel.uiState.value.mostrarDialogoEfectivo)
        assertEquals("No se pudo registrar la venta: sin conexion", viewModel.uiState.value.mensajeConfirmacion)
    }

    // Hallazgo de pruebas en el Xiaomi: la busqueda ahora consulta
    // InventarioRepository real, no un catalogo estatico - confirma que un
    // termino sin coincidencias reales muestra el error, no un falso
    // positivo de datos de ejemplo.
    @Test
    fun `buscar shows an error when the real inventory repository finds no match`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val viewModel = VentaViewModel(
            mockk(),
            inventarioRepository(articulos = emptyList()),
            preferences,
            SessionManager(),
            sucursalRepository(),
            ticketManager(),
        )

        viewModel.onBusquedaChange("coca")
        viewModel.buscar()

        assertNull(viewModel.uiState.value.articuloEncontrado)
        assertEquals("Artículo no encontrado", viewModel.uiState.value.errorBusqueda)
    }
}
