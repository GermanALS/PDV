package com.pdv.pos.usuario

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.auth.PasswordHasher
import com.pdv.pos.auth.Session
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.domain.model.Rol
import com.pdv.pos.domain.model.Usuario
import com.pdv.pos.domain.repository.RolRepository
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
    private val passwordHasher = PasswordHasher()

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

    private fun rolRepositoryConUnRol(): RolRepository {
        val repository = mockk<RolRepository>()
        every { repository.observeRoles() } returns MutableStateFlow(
            listOf(Rol(id = "rol-encargado-turno", nombre = "Encargado de turno", modulosPermitidos = listOf("venta"))),
        )
        return repository
    }

    @Test
    fun `onGuardarClick crea un usuario nuevo via el repositorio`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.iniciarSesion(Session("admin", "usuario-1", "rol-1"))
        val usuarioRepository = usuarioRepositoryVacio()
        coEvery { usuarioRepository.crearUsuario(any(), any(), any(), any()) } returns Unit
        val hasherMock = mockk<PasswordHasher>()
        coEvery { hasherMock.hash("secret123") } returns "hash-bcrypt-mockeado"
        val viewModel = UsuarioViewModel(usuarioRepository, rolRepositoryConUnRol(), preferences, sessionManager, hasherMock)

        viewModel.onUsernameChange("encargado1")
        viewModel.onNombreCompletoChange("Encargado de Turno")
        viewModel.onPasswordChange("secret123")
        viewModel.onGuardarClick()

        coVerify {
            usuarioRepository.crearUsuario(
                match { it.username == "encargado1" && it.nombreCompleto == "Encargado de Turno" && it.rolId == "rol-encargado-turno" },
                "hash-bcrypt-mockeado",
                "suc-1",
                "admin",
            )
        }
        assertEquals("Usuario creado", viewModel.uiState.value.mensajeConfirmacion)
        assertEquals("", viewModel.uiState.value.username)
    }

    // Observacion del usuario tras la verificacion en dispositivo (PLAN.md
    // Parte 13 sub-paso 4): no permitir crear un usuario sin contrasena.
    @Test
    fun `onGuardarClick no permite crear un usuario sin contrasena`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.iniciarSesion(Session("admin", "usuario-1", "rol-1"))
        val usuarioRepository = usuarioRepositoryVacio()
        val viewModel = UsuarioViewModel(usuarioRepository, rolRepositoryConUnRol(), preferences, sessionManager, passwordHasher)

        viewModel.onUsernameChange("encargado1")
        viewModel.onNombreCompletoChange("Encargado de Turno")
        viewModel.onGuardarClick()

        coVerify(exactly = 0) { usuarioRepository.crearUsuario(any(), any(), any(), any()) }
        assertEquals("La contraseña es obligatoria al crear un usuario", viewModel.uiState.value.error)
    }

    // PasswordHasher real usa Dispatchers.Default para no bloquear el hilo
    // principal (bcrypt cost 12 es deliberadamente lento) - un dispatcher
    // real no esta controlado por UnconfinedTestDispatcher, asi que aca se
    // mockea para no depender de tiempo real de reloj en el test.
    @Test
    fun `onGuardarClick hashea la contrasena escrita antes de crear el usuario`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.iniciarSesion(Session("admin", "usuario-1", "rol-1"))
        val usuarioRepository = usuarioRepositoryVacio()
        coEvery { usuarioRepository.crearUsuario(any(), any(), any(), any()) } returns Unit
        val hasherMock = mockk<PasswordHasher>()
        coEvery { hasherMock.hash("secret123") } returns "hash-bcrypt-mockeado"
        val viewModel = UsuarioViewModel(usuarioRepository, rolRepositoryConUnRol(), preferences, sessionManager, hasherMock)

        viewModel.onUsernameChange("encargado1")
        viewModel.onNombreCompletoChange("Encargado de Turno")
        viewModel.onPasswordChange("secret123")
        viewModel.onGuardarClick()

        coVerify { usuarioRepository.crearUsuario(any(), "hash-bcrypt-mockeado", "suc-1", "admin") }
    }

    @Test
    fun `onGuardarClick edita un usuario existente sin tocar la contrasena si el campo quedo vacio`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.iniciarSesion(Session("admin", "usuario-1", "rol-1"))
        val usuarioRepository = usuarioRepositoryVacio()
        coEvery { usuarioRepository.actualizarUsuario(any(), any(), any(), any()) } returns Unit
        val viewModel = UsuarioViewModel(usuarioRepository, rolRepositoryConUnRol(), preferences, sessionManager, passwordHasher)
        val usuarioExistente = Usuario(
            id = "usuario-1",
            username = "encargado1",
            nombreCompleto = "Encargado de Turno",
            rolId = "rol-encargado-turno",
            activo = true,
        )

        viewModel.onEditarClick(usuarioExistente)
        viewModel.onNombreCompletoChange("Nombre Editado")
        viewModel.onGuardarClick()

        coVerify {
            usuarioRepository.actualizarUsuario(
                match { it.id == "usuario-1" && it.nombreCompleto == "Nombre Editado" },
                null,
                "suc-1",
                "admin",
            )
        }
        coVerify(exactly = 0) { usuarioRepository.crearUsuario(any(), any(), any(), any()) }
        assertEquals("Usuario actualizado", viewModel.uiState.value.mensajeConfirmacion)
        assertNull(viewModel.uiState.value.usuarioEnEdicionId)
    }

    @Test
    fun `onEliminarClick elimina un usuario via el repositorio`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("suc-1")
        val sessionManager = SessionManager()
        sessionManager.iniciarSesion(Session("admin", "usuario-1", "rol-1"))
        val usuarioRepository = usuarioRepositoryVacio()
        coEvery { usuarioRepository.eliminarUsuario(any(), any(), any()) } returns Unit
        val viewModel = UsuarioViewModel(usuarioRepository, rolRepositoryConUnRol(), preferences, sessionManager, passwordHasher)

        viewModel.onEliminarClick("usuario-1")

        coVerify { usuarioRepository.eliminarUsuario("usuario-1", "suc-1", "admin") }
        assertEquals("Usuario eliminado", viewModel.uiState.value.mensajeConfirmacion)
    }

    @Test
    fun `onGuardarClick no llama al repositorio cuando falta sucursal o sesion`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        val sessionManager = SessionManager()
        val usuarioRepository = usuarioRepositoryVacio()
        val viewModel = UsuarioViewModel(usuarioRepository, rolRepositoryConUnRol(), preferences, sessionManager, passwordHasher)

        viewModel.onUsernameChange("encargado1")
        viewModel.onNombreCompletoChange("Encargado de Turno")
        viewModel.onPasswordChange("secret123")
        viewModel.onGuardarClick()

        coVerify(exactly = 0) { usuarioRepository.crearUsuario(any(), any(), any(), any()) }
        assertEquals("No se pudo guardar: falta sucursal o sesión activa", viewModel.uiState.value.error)
    }
}
