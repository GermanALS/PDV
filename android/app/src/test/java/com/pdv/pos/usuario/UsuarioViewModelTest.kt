package com.pdv.pos.usuario

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.domain.model.Usuario
import com.pdv.pos.domain.repository.UsuarioRepository
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

// UnconfinedTestDispatcher: mismo motivo que DevolucionViewModelTest -
// ConfiguracionPreferences hace I/O real de DataStore.
@OptIn(ExperimentalCoroutinesApi::class)
class UsuarioViewModelTest {

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

    private fun usuarioRepositoryVacio(): UsuarioRepository {
        val repository = mockk<UsuarioRepository>()
        every { repository.observeUsuarios() } returns MutableStateFlow(emptyList())
        return repository
    }

    @Test
    fun `onGuardarClick crea un usuario nuevo via el repositorio`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.login("admin", "password")
        val usuarioRepository = usuarioRepositoryVacio()
        coEvery { usuarioRepository.crearUsuario(any(), any(), any()) } returns Unit
        val viewModel = UsuarioViewModel(usuarioRepository, preferences, sessionManager)

        viewModel.onUsernameChange("encargado1")
        viewModel.onNombreCompletoChange("Encargado de Turno")
        viewModel.onGuardarClick()

        coVerify {
            usuarioRepository.crearUsuario(
                match { it.username == "encargado1" && it.nombreCompleto == "Encargado de Turno" },
                "suc-1",
                "admin",
            )
        }
        assertEquals("Usuario creado", viewModel.uiState.value.mensajeConfirmacion)
        assertEquals("", viewModel.uiState.value.username)
    }

    @Test
    fun `onGuardarClick edita un usuario existente via el repositorio`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.login("admin", "password")
        val usuarioRepository = usuarioRepositoryVacio()
        coEvery { usuarioRepository.actualizarUsuario(any(), any(), any()) } returns Unit
        val viewModel = UsuarioViewModel(usuarioRepository, preferences, sessionManager)
        val usuarioExistente = Usuario(
            id = "usuario-1",
            username = "encargado1",
            nombreCompleto = "Encargado de Turno",
            rol = "encargado_turno",
            activo = true,
        )

        viewModel.onEditarClick(usuarioExistente)
        viewModel.onNombreCompletoChange("Nombre Editado")
        viewModel.onGuardarClick()

        coVerify {
            usuarioRepository.actualizarUsuario(
                match { it.id == "usuario-1" && it.nombreCompleto == "Nombre Editado" },
                "suc-1",
                "admin",
            )
        }
        coVerify(exactly = 0) { usuarioRepository.crearUsuario(any(), any(), any()) }
        assertEquals("Usuario actualizado", viewModel.uiState.value.mensajeConfirmacion)
        assertNull(viewModel.uiState.value.usuarioEnEdicionId)
    }

    @Test
    fun `onEliminarClick elimina un usuario via el repositorio`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.login("admin", "password")
        val usuarioRepository = usuarioRepositoryVacio()
        coEvery { usuarioRepository.eliminarUsuario(any(), any(), any()) } returns Unit
        val viewModel = UsuarioViewModel(usuarioRepository, preferences, sessionManager)

        viewModel.onEliminarClick("usuario-1")

        coVerify { usuarioRepository.eliminarUsuario("usuario-1", "suc-1", "admin") }
        assertEquals("Usuario eliminado", viewModel.uiState.value.mensajeConfirmacion)
    }

    @Test
    fun `onGuardarClick no llama al repositorio cuando falta sucursal o sesion`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        val sessionManager = SessionManager()
        val usuarioRepository = usuarioRepositoryVacio()
        val viewModel = UsuarioViewModel(usuarioRepository, preferences, sessionManager)

        viewModel.onUsernameChange("encargado1")
        viewModel.onNombreCompletoChange("Encargado de Turno")
        viewModel.onGuardarClick()

        coVerify(exactly = 0) { usuarioRepository.crearUsuario(any(), any(), any()) }
        assertEquals("No se pudo guardar: falta sucursal o sesión activa", viewModel.uiState.value.error)
    }
}
