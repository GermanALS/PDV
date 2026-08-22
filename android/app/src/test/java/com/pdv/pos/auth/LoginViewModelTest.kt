package com.pdv.pos.auth

import com.pdv.pos.domain.model.Usuario
import com.pdv.pos.domain.repository.AuthRepository
import com.pdv.pos.domain.repository.LoginResultado
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun usuarioDeEjemplo() = Usuario(
        id = "usuario-1",
        username = "admin",
        nombreCompleto = "Administrador",
        rolId = "rol-admin",
        activo = true,
    )

    @Test
    fun `login with valid credentials starts a session and clears any previous error`() = runTest(dispatcher) {
        val authRepository = mockk<AuthRepository>()
        coEvery { authRepository.login("admin", "admin123") } returns
            LoginResultado.Exitoso(usuarioDeEjemplo(), accessToken = "token")
        val sessionManager = SessionManager()
        val viewModel = LoginViewModel(authRepository, sessionManager)
        viewModel.onUsernameChange("admin")
        viewModel.onPasswordChange("admin123")

        viewModel.login()

        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(Session("admin", "usuario-1", "rol-admin"), sessionManager.session.value)
    }

    @Test
    fun `login with invalid credentials exposes an error without starting a session`() = runTest(dispatcher) {
        val authRepository = mockk<AuthRepository>()
        coEvery { authRepository.login("admin", "wrong-password") } returns LoginResultado.CredencialesInvalidas
        val sessionManager = SessionManager()
        val viewModel = LoginViewModel(authRepository, sessionManager)
        viewModel.onUsernameChange("admin")
        viewModel.onPasswordChange("wrong-password")

        viewModel.login()

        assertEquals("Usuario o contraseña incorrectos", viewModel.uiState.value.errorMessage)
        assertNull(sessionManager.session.value)
    }

    @Test
    fun `login exposes a connection error without crashing when the backend is unreachable`() = runTest(dispatcher) {
        val authRepository = mockk<AuthRepository>()
        coEvery { authRepository.login(any(), any()) } throws IOException("sin conexion")
        val viewModel = LoginViewModel(authRepository, SessionManager())

        viewModel.login()

        assertEquals("No se pudo conectar al backend: sin conexion", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `typing after a failed attempt clears the previous error`() = runTest(dispatcher) {
        val authRepository = mockk<AuthRepository>()
        coEvery { authRepository.login(any(), any()) } returns LoginResultado.CredencialesInvalidas
        val viewModel = LoginViewModel(authRepository, SessionManager())
        viewModel.login()
        assertEquals("Usuario o contraseña incorrectos", viewModel.uiState.value.errorMessage)

        viewModel.onUsernameChange("admin")

        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `login calls the repository with the typed credentials`() = runTest(dispatcher) {
        val authRepository = mockk<AuthRepository>()
        coEvery { authRepository.login("admin", "admin123") } returns
            LoginResultado.Exitoso(usuarioDeEjemplo(), accessToken = "token")
        val viewModel = LoginViewModel(authRepository, SessionManager())
        viewModel.onUsernameChange("admin")
        viewModel.onPasswordChange("admin123")

        viewModel.login()

        coVerify { authRepository.login("admin", "admin123") }
    }
}
