package com.pdv.pos.ui

import com.pdv.pos.auth.Session
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.data.remote.ApiResult
import com.pdv.pos.data.remote.HealthApiService
import com.pdv.pos.data.remote.dto.HealthResponseDto
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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

    private fun sessionManager(username: String = "admin"): SessionManager {
        val sessionManager = mockk<SessionManager>(relaxed = true)
        every { sessionManager.session } returns MutableStateFlow(Session(username))
        return sessionManager
    }

    @Test
    fun `local greeting is set immediately without waiting on the network call`() {
        val api = mockk<HealthApiService>()
        coEvery { api.getHealth() } coAnswers {
            kotlinx.coroutines.delay(1000)
            HealthResponseDto(status = "ok", version = "0.1.0")
        }

        val viewModel = HelloViewModel(api, sessionManager())

        assertTrue(viewModel.uiState.value.localGreeting.isNotBlank())
    }

    @Test
    fun `fetchHealth exposes Success when the backend responds`() = runTest(dispatcher) {
        val api = mockk<HealthApiService>()
        coEvery { api.getHealth() } returns HealthResponseDto(status = "ok", version = "0.1.0")

        val viewModel = HelloViewModel(api, sessionManager())
        dispatcher.scheduler.advanceUntilIdle()

        val result = viewModel.uiState.value.healthResult
        assertEquals(ApiResult.Success("ok (v0.1.0)"), result)
    }

    @Test
    fun `fetchHealth exposes Error when the backend call fails`() = runTest(dispatcher) {
        val api = mockk<HealthApiService>()
        coEvery { api.getHealth() } throws IOException("sin conexion")

        val viewModel = HelloViewModel(api, sessionManager())
        dispatcher.scheduler.advanceUntilIdle()

        val result = viewModel.uiState.value.healthResult
        assertEquals(ApiResult.Error("sin conexion"), result)
    }

    @Test
    fun `uiState exposes the username from the active session`() {
        val api = mockk<HealthApiService>()
        coEvery { api.getHealth() } returns HealthResponseDto(status = "ok", version = "0.1.0")

        val viewModel = HelloViewModel(api, sessionManager(username = "user1"))

        assertEquals("user1", viewModel.uiState.value.username)
    }

    @Test
    fun `logout delegates to the session manager`() {
        val api = mockk<HealthApiService>()
        coEvery { api.getHealth() } returns HealthResponseDto(status = "ok", version = "0.1.0")
        val session = sessionManager()

        val viewModel = HelloViewModel(api, session)
        viewModel.logout()

        verify { session.logout() }
    }
}
