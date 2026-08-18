package com.pdv.pos.auth

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LoginViewModelTest {

    @Test
    fun `login with valid credentials clears any previous error`() {
        val sessionManager = mockk<SessionManager>()
        every { sessionManager.login("admin", "password") } returns true
        val viewModel = LoginViewModel(sessionManager)
        viewModel.onUsernameChange("admin")
        viewModel.onPasswordChange("password")

        viewModel.login()

        assertNull(viewModel.uiState.value.errorMessage)
        verify { sessionManager.login("admin", "password") }
    }

    @Test
    fun `login with invalid credentials exposes an error without crashing`() {
        val sessionManager = mockk<SessionManager>()
        every { sessionManager.login("admin", "wrong-password") } returns false
        val viewModel = LoginViewModel(sessionManager)
        viewModel.onUsernameChange("admin")
        viewModel.onPasswordChange("wrong-password")

        viewModel.login()

        assertEquals("Usuario o contraseña incorrectos", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `typing after a failed attempt clears the previous error`() {
        val sessionManager = mockk<SessionManager>()
        every { sessionManager.login(any(), any()) } returns false
        val viewModel = LoginViewModel(sessionManager)
        viewModel.login()
        assertEquals("Usuario o contraseña incorrectos", viewModel.uiState.value.errorMessage)

        viewModel.onUsernameChange("admin")

        assertNull(viewModel.uiState.value.errorMessage)
    }
}
