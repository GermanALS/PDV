package com.pdv.pos.auth

import com.pdv.pos.domain.repository.AuthRepository
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CambiarPasswordViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val sessionManager = SessionManager().apply {
        iniciarSesion(Session("admin", "usuario-1", "rol-1", debeCambiarPassword = true))
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModelCon(nueva: String, confirmacion: String) =
        CambiarPasswordViewModel(authRepository, sessionManager).apply {
            onNuevaPasswordChange(nueva)
            onConfirmacionChange(confirmacion)
        }

    @Test
    fun `a successful change clears the flag and keeps the session`() = runTest(dispatcher) {
        val viewModel = viewModelCon("nueva-segura", "nueva-segura")

        viewModel.guardar()

        coVerify(exactly = 1) { authRepository.cambiarPassword("usuario-1", "nueva-segura") }
        assertEquals(Session("admin", "usuario-1", "rol-1", debeCambiarPassword = false), sessionManager.session.value)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `a mismatched confirmation is rejected without calling the repository`() = runTest(dispatcher) {
        val viewModel = viewModelCon("nueva-segura", "otra")

        viewModel.guardar()

        assertEquals("Las contraseñas no coinciden", viewModel.uiState.value.errorMessage)
        coVerify(exactly = 0) { authRepository.cambiarPassword(any(), any()) }
    }

    @Test
    fun `the public bootstrap password cannot be chosen again`() = runTest(dispatcher) {
        val viewModel = viewModelCon(ADMIN_BOOTSTRAP_PASSWORD, ADMIN_BOOTSTRAP_PASSWORD)

        viewModel.guardar()

        assertEquals("La nueva contraseña no puede ser la inicial", viewModel.uiState.value.errorMessage)
        coVerify(exactly = 0) { authRepository.cambiarPassword(any(), any()) }
    }

    @Test
    fun `a network failure keeps the flag and shows the error`() = runTest(dispatcher) {
        coEvery { authRepository.cambiarPassword(any(), any()) } throws IOException("sin red")
        val viewModel = viewModelCon("nueva-segura", "nueva-segura")

        viewModel.guardar()

        assertEquals("No se pudo conectar con el servidor.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isGuardando)
        assertEquals(true, sessionManager.session.value?.debeCambiarPassword)
    }
}
