package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.AuthLoginRequestDto
import com.pdv.pos.data.remote.dto.AuthLoginResponseDto
import com.pdv.pos.data.remote.dto.UsuarioDto
import com.pdv.pos.domain.repository.LoginResultado
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Response
import kotlin.test.assertFailsWith

class RemoteAuthRepositoryTest {

    private fun httpError(code: Int) =
        HttpException(Response.error<Any>(code, "".toResponseBody("application/json".toMediaType())))

    @Test
    fun `login maps a successful response to Exitoso with the access token and usuario`() = runTest {
        val api = mockk<AuthApiService>()
        coEvery { api.login(AuthLoginRequestDto("admin", "admin123")) } returns AuthLoginResponseDto(
            accessToken = "token-jwt",
            tokenType = "bearer",
            usuario = UsuarioDto(
                id = "usuario-1",
                username = "admin",
                nombreCompleto = "Administrador",
                rolId = "rol-admin",
                activo = true,
                updatedAt = "2026-08-21T12:00:00Z",
            ),
        )
        val repository = RemoteAuthRepository(api)

        val resultado = repository.login("admin", "admin123")

        assertEquals(
            LoginResultado.Exitoso(
                usuario = com.pdv.pos.domain.model.Usuario(
                    id = "usuario-1",
                    username = "admin",
                    nombreCompleto = "Administrador",
                    rolId = "rol-admin",
                    activo = true,
                ),
                accessToken = "token-jwt",
            ),
            resultado,
        )
    }

    @Test
    fun `login maps a 401 response to CredencialesInvalidas`() = runTest {
        val api = mockk<AuthApiService>()
        coEvery { api.login(any()) } throws httpError(401)
        val repository = RemoteAuthRepository(api)

        val resultado = repository.login("admin", "contrasena-incorrecta")

        assertEquals(LoginResultado.CredencialesInvalidas, resultado)
    }

    @Test
    fun `login rethrows other HTTP errors instead of treating them as invalid credentials`() = runTest {
        val api = mockk<AuthApiService>()
        coEvery { api.login(any()) } throws httpError(500)
        val repository = RemoteAuthRepository(api)

        assertFailsWith<HttpException> { repository.login("admin", "admin123") }
    }

    @Test
    fun `login propagates network failures`() = runTest {
        val api = mockk<AuthApiService>()
        coEvery { api.login(any()) } throws java.io.IOException("sin conexion")
        val repository = RemoteAuthRepository(api)

        assertFailsWith<java.io.IOException> { repository.login("admin", "admin123") }
    }

    @Test
    fun `login sends the typed credentials as-is`() = runTest {
        val api = mockk<AuthApiService>()
        coEvery { api.login(any()) } throws httpError(401)
        val repository = RemoteAuthRepository(api)

        repository.login("admin", "admin123")

        coVerify { api.login(AuthLoginRequestDto("admin", "admin123")) }
    }
}
