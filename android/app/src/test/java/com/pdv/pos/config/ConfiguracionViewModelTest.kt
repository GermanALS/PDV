package com.pdv.pos.config

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.Sucursal
import com.pdv.pos.domain.repository.SucursalRepository
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
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
    private fun preferences(tempDir: File): ConfiguracionPreferences {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher + SupervisorJob()),
            produceFile = { File(tempDir, "test.preferences_pb") },
        )
        return ConfiguracionPreferences(dataStore)
    }

    private val fakeSucursalRepository = object : SucursalRepository {
        override fun observeSucursales(): Flow<List<Sucursal>> =
            flowOf(listOf(Sucursal(id = "s1", nombre = "Sucursal Test")))
    }

    @Test
    fun `selecting a mode from the UI persists it to DataStore and updates uiState`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        val viewModel = ConfiguracionViewModel(mockk(relaxed = true), preferences, fakeSucursalRepository)
        assertEquals(BackendMode.LOCAL, viewModel.uiState.value.modo)

        viewModel.onModoSelected(BackendMode.REMOTO)

        assertEquals(BackendMode.REMOTO, preferences.deviceConfig.first().backendMode)
        assertEquals(BackendMode.REMOTO, viewModel.uiState.value.modo)
    }

    @Test
    fun `logout delegates to the session manager`(@TempDir tempDir: File) = runTest(dispatcher) {
        val sessionManager = mockk<SessionManager>(relaxed = true)
        val viewModel = ConfiguracionViewModel(sessionManager, preferences(tempDir), fakeSucursalRepository)

        viewModel.logout()

        verify { sessionManager.logout() }
    }

    @Test
    fun `switching sucursal does not clobber an unsaved connection field edit`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        val viewModel = ConfiguracionViewModel(mockk(relaxed = true), preferences, fakeSucursalRepository)

        viewModel.onIpChange("192.168.1.50")
        viewModel.onSucursalSelected(Sucursal(id = "s1", nombre = "Sucursal Test"))

        assertEquals("192.168.1.50", viewModel.uiState.value.ip)
    }
}
