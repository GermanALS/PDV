package com.pdv.pos.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.auth.Session
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.remote.HealthApiService
import com.pdv.pos.data.remote.dto.HealthResponseDto
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.Rol
import com.pdv.pos.domain.model.Sucursal
import com.pdv.pos.domain.repository.RolRepository
import com.pdv.pos.domain.repository.SucursalRepository
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
class HelloViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sessionManager(username: String = "admin", rolId: String = "rol-admin"): SessionManager {
        val sessionManager = mockk<SessionManager>(relaxed = true)
        every { sessionManager.session } returns MutableStateFlow(Session(username, "usuario-1", rolId))
        return sessionManager
    }

    private fun rolRepository(roles: List<Rol> = emptyList()): RolRepository {
        val repository = mockk<RolRepository>()
        every { repository.observeRoles() } returns MutableStateFlow(roles)
        return repository
    }

    private fun sucursalRepository(sucursales: Flow<List<Sucursal>> = flowOf(emptyList())): SucursalRepository {
        val repository = mockk<SucursalRepository>()
        every { repository.observeSucursales() } returns sucursales
        return repository
    }

    private fun preferences(
        tempDir: File,
        sucursalId: String? = "suc-1",
        backendMode: BackendMode = BackendMode.LOCAL,
    ): ConfiguracionPreferences {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher + SupervisorJob()),
            produceFile = { File(tempDir, "test.preferences_pb") },
        )
        val preferences = ConfiguracionPreferences(dataStore)
        CoroutineScope(dispatcher).launch {
            preferences.setBackendMode(backendMode)
            if (sucursalId != null) preferences.setSucursalSeleccionada(sucursalId)
        }
        dispatcher.scheduler.advanceUntilIdle()
        return preferences
    }

    private fun appLogger(): AppLogger {
        val logger = mockk<AppLogger>()
        coEvery { logger.log(any(), any(), any(), any()) } returns Unit
        return logger
    }

    @Test
    fun `remoto shows connected when the backend responds`(@TempDir tempDir: File) = runTest(dispatcher) {
        val api = mockk<HealthApiService>()
        coEvery { api.getHealth() } returns HealthResponseDto(status = "ok", version = "0.1.0")

        val viewModel = HelloViewModel(
            api, sessionManager(), rolRepository(), sucursalRepository(), preferences(tempDir, backendMode = BackendMode.REMOTO), appLogger(),
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(EstadoConexion.CONECTADO, viewModel.uiState.value.conexion)
    }

    @Test
    fun `remoto shows no connection when the backend call fails`(@TempDir tempDir: File) = runTest(dispatcher) {
        val api = mockk<HealthApiService>()
        coEvery { api.getHealth() } throws IOException("sin conexion")

        val viewModel = HelloViewModel(
            api, sessionManager(), rolRepository(), sucursalRepository(), preferences(tempDir, backendMode = BackendMode.REMOTO), appLogger(),
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(EstadoConexion.SIN_CONEXION, viewModel.uiState.value.conexion)
    }

    @Test
    fun `local never queries the backend`(@TempDir tempDir: File) = runTest(dispatcher) {
        val api = mockk<HealthApiService>()

        val viewModel = HelloViewModel(
            api, sessionManager(), rolRepository(), sucursalRepository(), preferences(tempDir, backendMode = BackendMode.LOCAL), appLogger(),
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(BackendMode.LOCAL, viewModel.uiState.value.backendMode)
        assertEquals(EstadoConexion.SOLO_LOCAL, viewModel.uiState.value.conexion)
        coVerify(exactly = 0) { api.getHealth() }
    }

    @Test
    fun `switching from remoto to local shows local mode`(@TempDir tempDir: File) = runTest(dispatcher) {
        val api = mockk<HealthApiService>()
        coEvery { api.getHealth() } throws IOException("sin conexion")
        val preferences = preferences(tempDir, backendMode = BackendMode.REMOTO)
        val viewModel = HelloViewModel(api, sessionManager(), rolRepository(), sucursalRepository(), preferences, appLogger())
        dispatcher.scheduler.advanceUntilIdle()

        preferences.setBackendMode(BackendMode.LOCAL)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(BackendMode.LOCAL, viewModel.uiState.value.backendMode)
        assertEquals(EstadoConexion.SOLO_LOCAL, viewModel.uiState.value.conexion)
        coVerify(exactly = 1) { api.getHealth() }
    }

    @Test
    fun `uiState exposes the selected sucursal name, matching by remoteId too`(@TempDir tempDir: File) = runTest(dispatcher) {
        val api = mockk<HealthApiService>()
        val sucursales = flowOf(
            listOf(
                Sucursal(id = "local-9", nombre = "Otra"),
                Sucursal(id = "local-1", nombre = "Centro", remoteId = "suc-1"),
            ),
        )

        val viewModel = HelloViewModel(
            api, sessionManager(), rolRepository(), sucursalRepository(sucursales), preferences(tempDir), appLogger(),
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Centro", viewModel.uiState.value.sucursalNombre)
    }

    @Test
    fun `uiState has no sucursal name when none is selected`(@TempDir tempDir: File) = runTest(dispatcher) {
        val api = mockk<HealthApiService>()
        val sucursales = flowOf(listOf(Sucursal(id = "suc-1", nombre = "Centro")))

        val viewModel = HelloViewModel(
            api, sessionManager(), rolRepository(), sucursalRepository(sucursales), preferences(tempDir, sucursalId = null), appLogger(),
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.sucursalNombre)
    }

    @Test
    fun `a network failure loading sucursales leaves the name empty instead of crashing`(@TempDir tempDir: File) = runTest(dispatcher) {
        val api = mockk<HealthApiService>()
        coEvery { api.getHealth() } throws IOException("sin conexion")
        val sucursales = flow<List<Sucursal>> { throw IOException("sin conexion") }

        val viewModel = HelloViewModel(
            api, sessionManager(), rolRepository(), sucursalRepository(sucursales),
            preferences(tempDir, backendMode = BackendMode.REMOTO), appLogger(),
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.sucursalNombre)
        assertEquals(EstadoConexion.SIN_CONEXION, viewModel.uiState.value.conexion)
    }

    @Test
    fun `uiState exposes the rol name of the active session`(@TempDir tempDir: File) = runTest(dispatcher) {
        val api = mockk<HealthApiService>()
        val roles = listOf(Rol(id = "rol-encargado", nombre = "Encargado", modulosPermitidos = listOf("venta")))

        val viewModel = HelloViewModel(
            api, sessionManager(rolId = "rol-encargado"), rolRepository(roles), sucursalRepository(), preferences(tempDir), appLogger(),
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Encargado", viewModel.uiState.value.rolNombre)
    }

    @Test
    fun `uiState exposes the username from the active session`(@TempDir tempDir: File) {
        val api = mockk<HealthApiService>()
        coEvery { api.getHealth() } returns HealthResponseDto(status = "ok", version = "0.1.0")

        val viewModel = HelloViewModel(api, sessionManager(username = "user1"), rolRepository(), sucursalRepository(), preferences(tempDir), appLogger())

        assertEquals("user1", viewModel.uiState.value.username)
    }

    @Test
    fun `logout delegates to the session manager`(@TempDir tempDir: File) {
        val api = mockk<HealthApiService>()
        coEvery { api.getHealth() } returns HealthResponseDto(status = "ok", version = "0.1.0")
        val session = sessionManager()

        val viewModel = HelloViewModel(api, session, rolRepository(), sucursalRepository(), preferences(tempDir), appLogger())
        viewModel.logout()

        verify { session.logout() }
    }

    // Cierra el wiring diferido del sub-paso 1 (PLAN.md Parte 13): la sesion
    // real trae el rolId, y HelloViewModel expone solo los modulos que ese
    // rol permite.
    @Test
    fun `uiState exposes only the modules the session's rol allows`(@TempDir tempDir: File) = runTest(dispatcher) {
        val api = mockk<HealthApiService>()
        coEvery { api.getHealth() } returns HealthResponseDto(status = "ok", version = "0.1.0")
        val roles = listOf(Rol(id = "rol-encargado", nombre = "encargado_turno", modulosPermitidos = listOf("venta", "caja")))

        val viewModel = HelloViewModel(api, sessionManager(rolId = "rol-encargado"), rolRepository(roles), sucursalRepository(), preferences(tempDir), appLogger())
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf("venta", "caja"), viewModel.uiState.value.modulosPermitidos)
    }

    @Test
    fun `uiState exposes no modules when the session's rol is not found`(@TempDir tempDir: File) = runTest(dispatcher) {
        val api = mockk<HealthApiService>()
        coEvery { api.getHealth() } returns HealthResponseDto(status = "ok", version = "0.1.0")

        val viewModel = HelloViewModel(api, sessionManager(rolId = "rol-inexistente"), rolRepository(emptyList()), sucursalRepository(), preferences(tempDir), appLogger())
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.modulosPermitidos.isEmpty())
    }

    // PLAN.md Parte 13, checklist "Logging": un intento de navegar a un
    // modulo sin permiso se registra con categoria AUTH.
    @Test
    fun `onIntentoNavegar allows an allowed module without logging`(@TempDir tempDir: File) = runTest(dispatcher) {
        val api = mockk<HealthApiService>()
        coEvery { api.getHealth() } returns HealthResponseDto(status = "ok", version = "0.1.0")
        val roles = listOf(Rol(id = "rol-encargado", nombre = "encargado_turno", modulosPermitidos = listOf("venta")))
        val logger = appLogger()

        val viewModel = HelloViewModel(api, sessionManager(rolId = "rol-encargado"), rolRepository(roles), sucursalRepository(), preferences(tempDir), logger)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.onIntentoNavegar("venta"))
        dispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 0) { logger.log(any(), any(), any(), any()) }
    }

    @Test
    fun `onIntentoNavegar rejects a disallowed module and logs it with AUTH`(@TempDir tempDir: File) = runTest(dispatcher) {
        val api = mockk<HealthApiService>()
        coEvery { api.getHealth() } returns HealthResponseDto(status = "ok", version = "0.1.0")
        val roles = listOf(Rol(id = "rol-encargado", nombre = "encargado_turno", modulosPermitidos = listOf("venta")))
        val logger = appLogger()

        val viewModel = HelloViewModel(api, sessionManager(username = "admin", rolId = "rol-encargado"), rolRepository(roles), sucursalRepository(), preferences(tempDir), logger)
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.onIntentoNavegar("usuarios"))
        dispatcher.scheduler.advanceUntilIdle()
        coVerify { logger.log(LogType.AUTH, sucursalId = "suc-1", usuario = "admin", mensaje = any()) }
    }

    // Hallazgo de code-reviewer: sin sucursal seleccionada la denegacion no
    // debe perderse silenciosamente.
    @Test
    fun `onIntentoNavegar still logs the rejection when no sucursal is selected yet`(@TempDir tempDir: File) = runTest(dispatcher) {
        val api = mockk<HealthApiService>()
        coEvery { api.getHealth() } returns HealthResponseDto(status = "ok", version = "0.1.0")
        val roles = listOf(Rol(id = "rol-encargado", nombre = "encargado_turno", modulosPermitidos = listOf("venta")))
        val logger = appLogger()

        val viewModel = HelloViewModel(
            api,
            sessionManager(username = "admin", rolId = "rol-encargado"),
            rolRepository(roles),
            sucursalRepository(),
            preferences(tempDir, sucursalId = null),
            logger,
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.onIntentoNavegar("usuarios"))
        dispatcher.scheduler.advanceUntilIdle()
        coVerify { logger.log(LogType.AUTH, sucursalId = "-", usuario = "admin", mensaje = any()) }
    }
}
