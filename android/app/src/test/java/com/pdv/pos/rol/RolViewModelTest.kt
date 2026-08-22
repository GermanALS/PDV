package com.pdv.pos.rol

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.auth.Session
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.domain.model.Rol
import com.pdv.pos.domain.repository.RolRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
class RolViewModelTest {

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

    private fun rolRepositoryVacio(): RolRepository {
        val repository = mockk<RolRepository>()
        every { repository.observeRoles() } returns MutableStateFlow(emptyList())
        return repository
    }

    @Test
    fun `onGuardarClick crea un rol nuevo via el repositorio`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.iniciarSesion(Session("admin", "usuario-1", "rol-1"))
        val rolRepository = rolRepositoryVacio()
        coEvery { rolRepository.crearRol(any(), any(), any()) } returns Unit
        val viewModel = RolViewModel(rolRepository, preferences, sessionManager)

        viewModel.onNombreChange("Cajero")
        viewModel.onModuloToggle("venta")
        viewModel.onModuloToggle("caja")
        viewModel.onGuardarClick()

        coVerify {
            rolRepository.crearRol(
                match { it.nombre == "Cajero" && it.modulosPermitidos.toSet() == setOf("venta", "caja") },
                "suc-1",
                "admin",
            )
        }
        assertEquals("Rol creado", viewModel.uiState.value.mensajeConfirmacion)
    }

    @Test
    fun `onGuardarClick no llama al repositorio sin modulos seleccionados`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.iniciarSesion(Session("admin", "usuario-1", "rol-1"))
        val rolRepository = rolRepositoryVacio()
        val viewModel = RolViewModel(rolRepository, preferences, sessionManager)

        viewModel.onNombreChange("Cajero")
        viewModel.onGuardarClick()

        coVerify(exactly = 0) { rolRepository.crearRol(any(), any(), any()) }
        assertEquals("Completa el nombre y selecciona al menos un módulo", viewModel.uiState.value.error)
    }

    @Test
    fun `onEditarClick de un rol de sistema deja el formulario en solo lectura`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        val sessionManager = SessionManager()
        val rolRepository = rolRepositoryVacio()
        val viewModel = RolViewModel(rolRepository, preferences, sessionManager)
        val rolSistema = Rol(id = "rol-admin", nombre = "administrador", modulosPermitidos = listOf("venta"), esSistema = true)

        viewModel.onEditarClick(rolSistema)

        assertEquals(true, viewModel.uiState.value.esSistemaEnEdicion)
    }

    @Test
    fun `onEliminarClick elimina un rol via el repositorio`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.iniciarSesion(Session("admin", "usuario-1", "rol-1"))
        val rolRepository = rolRepositoryVacio()
        coEvery { rolRepository.eliminarRol(any(), any(), any()) } returns Unit
        val viewModel = RolViewModel(rolRepository, preferences, sessionManager)

        viewModel.onEliminarClick("rol-1")

        coVerify { rolRepository.eliminarRol("rol-1", "suc-1", "admin") }
        assertEquals("Rol eliminado", viewModel.uiState.value.mensajeConfirmacion)
    }
}
