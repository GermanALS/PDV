package com.pdv.pos.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.work.WorkInfo
import com.pdv.pos.auth.Session
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.data.remote.ApiResult
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.Sucursal
import com.pdv.pos.domain.model.Rol
import com.pdv.pos.domain.repository.AuthRepository
import com.pdv.pos.domain.repository.LoginResultado
import com.pdv.pos.domain.repository.RolRepository
import com.pdv.pos.domain.repository.SucursalRepository
import com.pdv.pos.domain.model.Usuario
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import com.pdv.pos.ia.LlmClient
import com.pdv.pos.ia.LlmProvider
import com.pdv.pos.ia.PROMPT_SISTEMA_DEFAULT
import com.pdv.pos.sync.ResumenPendientes
import com.pdv.pos.sync.SyncPendientesResumen
import com.pdv.pos.sync.SyncScheduler
import com.pdv.pos.sync.SyncState
import com.pdv.pos.sync.SyncStateStore
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

// UnconfinedTestDispatcher (no StandardTestDispatcher): ConfiguracionPreferences
// hace I/O real de DataStore, que reanuda en un hilo propio fuera del reloj
// virtual de un StandardTestDispatcher - el patron recomendado para
// ViewModels con corutinas reales es UnconfinedTestDispatcher.
@OptIn(ExperimentalCoroutinesApi::class)
class ConfiguracionViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // scope = dispatcher (no el Dispatchers.IO real por defecto): el actor
    // interno de DataStore corre en el mismo dispatcher de prueba que
    // viewModelScope, para que las escrituras sean observables de forma
    // sincronica en el test en vez de terminar en un hilo real sin trackear.
    // Mismo DataStore<Preferences> subyacente para ConfiguracionPreferences e
    // IaPreferences, como en produccion (DataStoreModule provee un unico
    // DataStore compartido, cada clase usa sus propias keys).
    private fun dataStore(tempDir: File): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(dispatcher + SupervisorJob()),
        produceFile = { File(tempDir, "test.preferences_pb") },
    )

    // FakeTokenCipher: AndroidKeystoreTokenCipher no es testeable en JVM
    // (PLAN.md Parte 14, sub-paso 3).
    private fun fakeIaPreferences(dataStore: DataStore<Preferences>): IaPreferences =
        IaPreferences(dataStore, FakeTokenCipher())

    private val fakeSucursalRepository = object : SucursalRepository {
        override fun observeSucursales(): Flow<List<Sucursal>> =
            flowOf(listOf(Sucursal(id = "s1", nombre = "Sucursal Test")))
    }

    private fun viewModel(
        tempDir: File,
        sessionManager: SessionManager = mockk(relaxed = true),
        llmClient: LlmClient = mockk(relaxed = true),
        authRepository: AuthRepository = mockk(relaxed = true),
        syncStateStore: SyncStateStore = mockk(relaxed = true),
        syncPendientesResumen: SyncPendientesResumen = mockk(relaxed = true),
        syncScheduler: SyncScheduler = mockk(relaxed = true),
    ): ConfiguracionViewModel {
        val dataStore = dataStore(tempDir)
        return ConfiguracionViewModel(
            sessionManager,
            ConfiguracionPreferences(dataStore),
            fakeSucursalRepository,
            fakeIaPreferences(dataStore),
            llmClient,
            PromptIaPreferences(dataStore),
            authRepository,
            mockk(relaxed = true),
            mockk(relaxed = true),
            syncStateStore,
            syncPendientesResumen,
            syncScheduler,
        )
    }

    private fun viewModelConGate(
        dataStore: DataStore<Preferences>,
        session: Session?,
        roles: List<Rol>,
        appLogger: AppLogger,
    ): ConfiguracionViewModel {
        val sessionManager = mockk<SessionManager>(relaxed = true)
        every { sessionManager.session } returns MutableStateFlow(session).asStateFlow()
        val rolRepository = mockk<RolRepository>()
        every { rolRepository.observeRoles() } returns flowOf(roles)
        return ConfiguracionViewModel(
            sessionManager,
            ConfiguracionPreferences(dataStore),
            fakeSucursalRepository,
            fakeIaPreferences(dataStore),
            mockk(relaxed = true),
            PromptIaPreferences(dataStore),
            mockk(relaxed = true),
            rolRepository,
            appLogger,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
        )
    }

    @Test
    fun `onIntentoAbrirConflictos returns true when the session role includes the usuarios module`(@TempDir tempDir: File) = runTest(dispatcher) {
        val appLogger = mockk<AppLogger>(relaxed = true)
        val viewModel = viewModelConGate(
            dataStore(tempDir),
            Session(username = "admin", usuarioId = "u1", rolId = "r1"),
            listOf(Rol(id = "r1", nombre = "Admin", modulosPermitidos = listOf("usuarios", "configuracion"))),
            appLogger,
        )

        assertEquals(true, viewModel.onIntentoAbrirConflictos())
        coVerify(exactly = 0) { appLogger.log(any(), any(), any(), any()) }
    }

    @Test
    fun `onIntentoAbrirConflictos returns false and logs an AUTH denial when the role lacks the usuarios module`(@TempDir tempDir: File) = runTest(dispatcher) {
        val appLogger = mockk<AppLogger>(relaxed = true)
        val viewModel = viewModelConGate(
            dataStore(tempDir),
            Session(username = "cajero", usuarioId = "u2", rolId = "r2"),
            listOf(Rol(id = "r2", nombre = "Cajero", modulosPermitidos = listOf("venta"))),
            appLogger,
        )

        assertEquals(false, viewModel.onIntentoAbrirConflictos())
        coVerify { appLogger.log(LogType.AUTH, any(), "cajero", any()) }
    }

    @Test
    fun `selecting a mode from the UI persists it to DataStore and updates uiState`(@TempDir tempDir: File) = runTest(dispatcher) {
        val dataStore = dataStore(tempDir)
        val preferences = ConfiguracionPreferences(dataStore)
        val viewModel = ConfiguracionViewModel(
            mockk(relaxed = true),
            preferences,
            fakeSucursalRepository,
            fakeIaPreferences(dataStore),
            mockk(relaxed = true),
            PromptIaPreferences(dataStore),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
        )
        assertEquals(BackendMode.LOCAL, viewModel.uiState.value.modo)

        viewModel.onModoSelected(BackendMode.REMOTO)

        assertEquals(BackendMode.REMOTO, preferences.deviceConfig.first().backendMode)
        assertEquals(BackendMode.REMOTO, viewModel.uiState.value.modo)
    }

    @Test
    fun `switching sucursal does not clobber an unsaved connection field edit`(@TempDir tempDir: File) = runTest(dispatcher) {
        val viewModel = viewModel(tempDir)

        viewModel.onIpChange("192.168.1.50")
        viewModel.onSucursalSelected(Sucursal(id = "s1", nombre = "Sucursal Test"))

        assertEquals("192.168.1.50", viewModel.uiState.value.ip)
    }

    @Test
    fun `in a non-LOCAL mode a saved sucursal absent from the catalog is reconciled to the fallback`(@TempDir tempDir: File) = runTest(dispatcher) {
        val dataStore = dataStore(tempDir)
        val preferences = ConfiguracionPreferences(dataStore)
        preferences.setSucursalSeleccionada("sucursal-local-vieja")
        preferences.setBackendMode(BackendMode.REMOTO)

        ConfiguracionViewModel(
            mockk(relaxed = true),
            preferences,
            fakeSucursalRepository,
            fakeIaPreferences(dataStore),
            mockk(relaxed = true),
            PromptIaPreferences(dataStore),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
        )

        assertEquals("s1", preferences.deviceConfig.first().sucursalIdSeleccionada)
    }

    @Test
    fun `in LOCAL mode the saved sucursal id is left untouched`(@TempDir tempDir: File) = runTest(dispatcher) {
        val dataStore = dataStore(tempDir)
        val preferences = ConfiguracionPreferences(dataStore)
        preferences.setSucursalSeleccionada("sucursal-local-vieja")

        ConfiguracionViewModel(
            mockk(relaxed = true),
            preferences,
            fakeSucursalRepository,
            fakeIaPreferences(dataStore),
            mockk(relaxed = true),
            PromptIaPreferences(dataStore),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
        )

        assertEquals("sucursal-local-vieja", preferences.deviceConfig.first().sucursalIdSeleccionada)
    }

    @Test
    fun `saving an ia token persists it, clears the draft and never re-exposes it`(@TempDir tempDir: File) = runTest(dispatcher) {
        val dataStore = dataStore(tempDir)
        val iaPreferences = fakeIaPreferences(dataStore)
        val viewModel = ConfiguracionViewModel(
            mockk(relaxed = true),
            ConfiguracionPreferences(dataStore),
            fakeSucursalRepository,
            iaPreferences,
            mockk(relaxed = true),
            PromptIaPreferences(dataStore),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
        )

        viewModel.onIaTokenInputChange("token-secreto")
        viewModel.onGuardarIa()

        assertEquals("token-secreto", iaPreferences.getToken())
        assertEquals("", viewModel.uiState.value.iaTokenInput)
        assertEquals(true, viewModel.uiState.value.iaTieneTokenGuardado)
    }

    @Test
    fun `saving without touching the token field keeps the previously saved token`(@TempDir tempDir: File) = runTest(dispatcher) {
        val dataStore = dataStore(tempDir)
        val iaPreferences = fakeIaPreferences(dataStore)
        iaPreferences.setToken("token-existente")
        val viewModel = ConfiguracionViewModel(
            mockk(relaxed = true),
            ConfiguracionPreferences(dataStore),
            fakeSucursalRepository,
            iaPreferences,
            mockk(relaxed = true),
            PromptIaPreferences(dataStore),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
        )

        viewModel.onIaActivoChange(true)
        viewModel.onGuardarIa()

        assertEquals("token-existente", iaPreferences.getToken())
    }

    @Test
    fun `a fresh install shows the provider default model instead of a blank field`(@TempDir tempDir: File) = runTest(dispatcher) {
        val viewModel = viewModel(tempDir)

        assertEquals(LlmProvider.DEEP_SEEK.modeloPorDefecto, viewModel.uiState.value.iaModelo)
    }

    @Test
    fun `switching provider resets the model field to the new provider's default`(@TempDir tempDir: File) = runTest(dispatcher) {
        val viewModel = viewModel(tempDir)

        viewModel.onIaModeloChange("deepseek-reasoner")
        viewModel.onIaProveedorSelected(LlmProvider.OPEN_AI)

        assertEquals(LlmProvider.OPEN_AI.modeloPorDefecto, viewModel.uiState.value.iaModelo)
    }

    @Test
    fun `re-selecting the already active provider does not clobber a custom model`(@TempDir tempDir: File) = runTest(dispatcher) {
        val viewModel = viewModel(tempDir)

        viewModel.onIaModeloChange("deepseek-reasoner")
        viewModel.onIaProveedorSelected(LlmProvider.DEEP_SEEK)

        assertEquals("deepseek-reasoner", viewModel.uiState.value.iaModelo)
    }

    @Test
    fun `testing the connection uses a custom model for a non-OpenRouter provider`(@TempDir tempDir: File) = runTest(dispatcher) {
        val llmClient = mockk<LlmClient>()
        coEvery { llmClient.probarConectividad(any(), any(), any()) } returns ApiResult.Success("4")
        val viewModel = viewModel(tempDir, llmClient = llmClient)

        viewModel.onIaModeloChange("deepseek-reasoner")
        viewModel.onProbarConexionIa()

        coVerify { llmClient.probarConectividad(LlmProvider.DEEP_SEEK, "", "deepseek-reasoner") }
    }

    @Test
    fun `testing the connection reports success in the ui state`(@TempDir tempDir: File) = runTest(dispatcher) {
        val llmClient = mockk<LlmClient>()
        coEvery { llmClient.probarConectividad(any(), any(), any()) } returns ApiResult.Success("4")
        val viewModel = viewModel(tempDir, llmClient = llmClient)

        viewModel.onIaTokenInputChange("token")
        viewModel.onProbarConexionIa()

        assertEquals(ApiResult.Success("4"), viewModel.uiState.value.iaResultadoPrueba)
        assertEquals(false, viewModel.uiState.value.iaProbandoConexion)
    }

    @Test
    fun `testing the connection with an unsaved draft token uses that draft, not a stale saved one`(@TempDir tempDir: File) = runTest(dispatcher) {
        val llmClient = mockk<LlmClient>()
        coEvery { llmClient.probarConectividad(any(), any(), any()) } returns ApiResult.Success("4")
        val viewModel = viewModel(tempDir, llmClient = llmClient)

        viewModel.onIaTokenInputChange("token-sin-guardar")
        viewModel.onProbarConexionIa()

        coVerify { llmClient.probarConectividad(LlmProvider.DEEP_SEEK, "token-sin-guardar", LlmProvider.DEEP_SEEK.modeloPorDefecto) }
    }

    @Test
    fun `testing the connection reports the error in the ui state`(@TempDir tempDir: File) = runTest(dispatcher) {
        val llmClient = mockk<LlmClient>()
        coEvery { llmClient.probarConectividad(any(), any(), any()) } returns ApiResult.Error("Token invalido o sin permiso para el proveedor seleccionado")
        val viewModel = viewModel(tempDir, llmClient = llmClient)

        viewModel.onProbarConexionIa()

        assertEquals(
            ApiResult.Error("Token invalido o sin permiso para el proveedor seleccionado"),
            viewModel.uiState.value.iaResultadoPrueba,
        )
    }

    @Test
    fun `a fresh install seeds the prompt field with the approved default text`(@TempDir tempDir: File) = runTest(dispatcher) {
        val viewModel = viewModel(tempDir)

        assertEquals(PROMPT_SISTEMA_DEFAULT, viewModel.uiState.value.promptIa)
    }

    @Test
    fun `saving the prompt after successful reauthentication persists the new text`(@TempDir tempDir: File) = runTest(dispatcher) {
        val dataStore = dataStore(tempDir)
        val promptIaPreferences = PromptIaPreferences(dataStore)
        val sessionManager = mockk<SessionManager>(relaxed = true)
        every { sessionManager.session } returns MutableStateFlow(Session(username = "admin", usuarioId = "u1", rolId = "r1")).asStateFlow()
        val authRepository = mockk<AuthRepository>()
        coEvery { authRepository.login("admin", "password") } returns
            LoginResultado.Exitoso(Usuario(id = "u1", username = "admin", nombreCompleto = "Admin", rolId = "r1"), accessToken = null)
        val viewModel = ConfiguracionViewModel(
            sessionManager,
            ConfiguracionPreferences(dataStore),
            fakeSucursalRepository,
            fakeIaPreferences(dataStore),
            mockk(relaxed = true),
            promptIaPreferences,
            authRepository,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
        )

        viewModel.onPromptIaChange("Prompt nuevo")
        viewModel.onPromptIaPasswordChange("password")
        viewModel.onGuardarPromptIa()

        assertEquals("Prompt nuevo", promptIaPreferences.prompt.first())
        assertEquals("", viewModel.uiState.value.promptIaPasswordInput)
    }

    @Test
    fun `saving the prompt with the wrong password rejects the change and reports an error`(@TempDir tempDir: File) = runTest(dispatcher) {
        val dataStore = dataStore(tempDir)
        val promptIaPreferences = PromptIaPreferences(dataStore)
        val sessionManager = mockk<SessionManager>(relaxed = true)
        every { sessionManager.session } returns MutableStateFlow(Session(username = "admin", usuarioId = "u1", rolId = "r1")).asStateFlow()
        val authRepository = mockk<AuthRepository>()
        coEvery { authRepository.login("admin", "incorrecta") } returns LoginResultado.CredencialesInvalidas
        val viewModel = ConfiguracionViewModel(
            sessionManager,
            ConfiguracionPreferences(dataStore),
            fakeSucursalRepository,
            fakeIaPreferences(dataStore),
            mockk(relaxed = true),
            promptIaPreferences,
            authRepository,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
        )

        viewModel.onPromptIaChange("Prompt nuevo")
        viewModel.onPromptIaPasswordChange("incorrecta")
        viewModel.onGuardarPromptIa()

        assertEquals(PROMPT_SISTEMA_DEFAULT, promptIaPreferences.prompt.first())
        assertEquals("Contraseña incorrecta, cambio no guardado.", viewModel.uiState.value.promptIaError)
    }

    @Test
    fun `selecting a mode with nothing pending applies it immediately, no dialog`(@TempDir tempDir: File) = runTest(dispatcher) {
        val resumen = mockk<SyncPendientesResumen>()
        coEvery { resumen.calcular() } returns ResumenPendientes(emptyList())
        val viewModel = viewModel(tempDir, syncPendientesResumen = resumen)

        viewModel.onModoSelected(BackendMode.REMOTO)

        assertEquals(BackendMode.REMOTO, viewModel.uiState.value.modo)
        assertEquals(null, viewModel.uiState.value.dialogoCambioModo)
    }

    @Test
    fun `selecting a mode with pending rows opens the confirmation dialog instead of applying it`(@TempDir tempDir: File) =
        runTest(dispatcher) {
            val resumen = mockk<SyncPendientesResumen>()
            coEvery { resumen.calcular() } returns ResumenPendientes(listOf("ventas" to 2))
            val viewModel = viewModel(tempDir, syncPendientesResumen = resumen)

            viewModel.onModoSelected(BackendMode.REMOTO)

            assertEquals(BackendMode.LOCAL, viewModel.uiState.value.modo)
            assertEquals(BackendMode.REMOTO, viewModel.uiState.value.dialogoCambioModo?.modoDestino)
        }

    @Test
    fun `confirming the mode change applies it and closes the dialog`(@TempDir tempDir: File) = runTest(dispatcher) {
        val resumen = mockk<SyncPendientesResumen>()
        coEvery { resumen.calcular() } returns ResumenPendientes(listOf("ventas" to 2))
        val viewModel = viewModel(tempDir, syncPendientesResumen = resumen)
        viewModel.onModoSelected(BackendMode.REMOTO)

        viewModel.onConfirmarCambioModo()

        assertEquals(BackendMode.REMOTO, viewModel.uiState.value.modo)
        assertEquals(null, viewModel.uiState.value.dialogoCambioModo)
    }

    @Test
    fun `cancelling the mode change closes the dialog without changing the mode`(@TempDir tempDir: File) = runTest(dispatcher) {
        val resumen = mockk<SyncPendientesResumen>()
        coEvery { resumen.calcular() } returns ResumenPendientes(listOf("ventas" to 2))
        val viewModel = viewModel(tempDir, syncPendientesResumen = resumen)
        viewModel.onModoSelected(BackendMode.REMOTO)

        viewModel.onCancelarCambioModo()

        assertEquals(BackendMode.LOCAL, viewModel.uiState.value.modo)
        assertEquals(null, viewModel.uiState.value.dialogoCambioModo)
    }

    @Test
    fun `sync section reflects SyncStateStore and the pending total`(@TempDir tempDir: File) = runTest(dispatcher) {
        val syncStateStore = mockk<SyncStateStore>()
        every { syncStateStore.state } returns
            flowOf(SyncState(lastSuccessAtMillis = 1_700_000_000_000L, lastError = "timeout"))
        val resumen = mockk<SyncPendientesResumen>()
        coEvery { resumen.calcular() } returns ResumenPendientes(listOf("ventas" to 2, "entradas" to 1))

        val viewModel = viewModel(tempDir, syncStateStore = syncStateStore, syncPendientesResumen = resumen)

        assertEquals(1_700_000_000_000L, viewModel.uiState.value.syncUltimoExitoMillis)
        assertEquals("timeout", viewModel.uiState.value.syncUltimoError)
        assertEquals(3, viewModel.uiState.value.syncPendientesTotal)
    }

    @Test
    fun `sincronizar ahora delegates to the SyncScheduler`(@TempDir tempDir: File) = runTest(dispatcher) {
        val syncScheduler = mockk<SyncScheduler>(relaxed = true)
        val viewModel = viewModel(tempDir, syncScheduler = syncScheduler)

        viewModel.onSincronizarAhora()

        verify(exactly = 1) { syncScheduler.sincronizarAhora() }
    }

    @Test
    fun `sync section shows progress while the manual sync runs and refreshes pendientes on completion`(
        @TempDir tempDir: File,
    ) = runTest(dispatcher) {
        val trabajoInmediato = MutableStateFlow<WorkInfo.State?>(null)
        val syncScheduler = mockk<SyncScheduler>(relaxed = true)
        every { syncScheduler.observarTrabajoInmediato() } returns trabajoInmediato
        val resumen = mockk<SyncPendientesResumen>()
        coEvery { resumen.calcular() } returns ResumenPendientes(emptyList())
        val viewModel = viewModel(tempDir, syncScheduler = syncScheduler, syncPendientesResumen = resumen)

        trabajoInmediato.value = WorkInfo.State.RUNNING
        assertEquals(true, viewModel.uiState.value.syncSincronizando)

        trabajoInmediato.value = WorkInfo.State.SUCCEEDED

        assertEquals(false, viewModel.uiState.value.syncSincronizando)
        // Una vez en init() y otra al terminar el work (PLAN.md Parte 32,
        // hallazgo de verificacion en dispositivo: el boton no refrescaba
        // los pendientes al terminar).
        coVerify(exactly = 2) { resumen.calcular() }
    }
}
