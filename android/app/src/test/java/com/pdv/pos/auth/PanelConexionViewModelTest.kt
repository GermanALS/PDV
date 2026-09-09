package com.pdv.pos.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.remote.ApiResult
import com.pdv.pos.data.remote.BackendHealthChecker
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.EsquemaConexion
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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

// El panel de conexion del login (PLAN.md Parte 31) no recibe SessionManager
// ni AuthRepository: exponer y guardar la configuracion no depende de sesion.
// Mismo patron de dispatcher/DataStore que ConfiguracionViewModelTest.
@OptIn(ExperimentalCoroutinesApi::class)
class PanelConexionViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun dataStore(tempDir: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher + SupervisorJob()),
            produceFile = { File(tempDir, "test.preferences_pb") },
        )

    private fun viewModel(
        preferences: ConfiguracionPreferences,
        healthChecker: BackendHealthChecker = mockk(),
    ): PanelConexionViewModel = PanelConexionViewModel(preferences, healthChecker)

    @Test
    fun `seeds the draft fields from the persisted device config`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = ConfiguracionPreferences(dataStore(tempDir))
        preferences.setBackendMode(BackendMode.REMOTO)
        preferences.setConexion(EsquemaConexion.HTTPS, ip = "10.0.0.9", puerto = "9443")

        val vm = viewModel(preferences)

        val estado = vm.uiState.value
        assertEquals(BackendMode.REMOTO, estado.modo)
        assertEquals(EsquemaConexion.HTTPS, estado.esquema)
        assertEquals("10.0.0.9", estado.host)
        assertEquals("9443", estado.puerto)
    }

    @Test
    fun `saving the connection persists to the shared preferences without a session`(@TempDir tempDir: File) =
        runTest(dispatcher) {
            val preferences = ConfiguracionPreferences(dataStore(tempDir))
            val vm = viewModel(preferences)

            vm.onEsquemaSelected(EsquemaConexion.HTTPS)
            vm.onHostChange("192.168.1.5")
            vm.onPuertoChange("8000")
            vm.onGuardar()

            val persistido = preferences.deviceConfig.first()
            assertEquals(EsquemaConexion.HTTPS, persistido.esquema)
            assertEquals("192.168.1.5", persistido.ip)
            assertEquals("8000", persistido.puerto)
        }

    @Test
    fun `switching mode persists immediately without a session or backend`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = ConfiguracionPreferences(dataStore(tempDir))
        preferences.setBackendMode(BackendMode.REMOTO)
        val vm = viewModel(preferences)

        vm.onModoSelected(BackendMode.LOCAL)

        assertEquals(BackendMode.LOCAL, preferences.deviceConfig.first().backendMode)
        assertEquals(BackendMode.LOCAL, vm.uiState.value.modo)
    }

    @Test
    fun `a mid-edit host is not clobbered by a later mode change`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = ConfiguracionPreferences(dataStore(tempDir))
        val vm = viewModel(preferences)

        vm.onHostChange("192.168.1.50")
        vm.onModoSelected(BackendMode.REMOTO)

        assertEquals("192.168.1.50", vm.uiState.value.host)
    }

    @Test
    fun `probar conexion exposes the checker result and clears the probando flag`(@TempDir tempDir: File) =
        runTest(dispatcher) {
            val preferences = ConfiguracionPreferences(dataStore(tempDir))
            val healthChecker = mockk<BackendHealthChecker>()
            coEvery { healthChecker.probar(any(), any(), any()) } returns ApiResult.Success("""{"status":"ok"}""")
            val vm = viewModel(preferences, healthChecker)

            vm.onProbarConexion()

            assertTrue(vm.uiState.value.resultadoPrueba is ApiResult.Success)
            assertFalse(vm.uiState.value.probando)
        }

    @Test
    fun `probar conexion passes the draft scheme host and port to the checker`(@TempDir tempDir: File) =
        runTest(dispatcher) {
            val preferences = ConfiguracionPreferences(dataStore(tempDir))
            val healthChecker = mockk<BackendHealthChecker>()
            coEvery { healthChecker.probar(any(), any(), any()) } returns ApiResult.Error("irrelevante")
            val vm = viewModel(preferences, healthChecker)

            vm.onEsquemaSelected(EsquemaConexion.HTTPS)
            vm.onHostChange("backend.example.com")
            vm.onPuertoChange("443")
            vm.onProbarConexion()

            coVerify { healthChecker.probar(EsquemaConexion.HTTPS, "backend.example.com", "443") }
        }
}
