package com.pdv.pos.data.remote

import com.pdv.pos.auth.PasswordHasher
import com.pdv.pos.data.remote.dto.AuthCambiarPasswordRequestDto
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
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
        val repository = RemoteAuthRepository(api, PasswordHasher())

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
        val repository = RemoteAuthRepository(api, PasswordHasher())

        val resultado = repository.login("admin", "contrasena-incorrecta")

        assertEquals(LoginResultado.CredencialesInvalidas, resultado)
    }

    @Test
    fun `login rethrows other HTTP errors instead of treating them as invalid credentials`() = runTest {
        val api = mockk<AuthApiService>()
        coEvery { api.login(any()) } throws httpError(500)
        val repository = RemoteAuthRepository(api, PasswordHasher())

        assertFailsWith<HttpException> { repository.login("admin", "admin123") }
    }

    @Test
    fun `login propagates network failures`() = runTest {
        val api = mockk<AuthApiService>()
        coEvery { api.login(any()) } throws java.io.IOException("sin conexion")
        val repository = RemoteAuthRepository(api, PasswordHasher())

        assertFailsWith<java.io.IOException> { repository.login("admin", "admin123") }
    }

    @Test
    fun `login sends the typed credentials as-is`() = runTest {
        val api = mockk<AuthApiService>()
        coEvery { api.login(any()) } throws httpError(401)
        val repository = RemoteAuthRepository(api, PasswordHasher())

        repository.login("admin", "admin123")

        coVerify { api.login(AuthLoginRequestDto("admin", "admin123")) }
    }

    @Test
    fun `cambiarPassword sends a bcrypt hash of the new password, never the plain text`() = runTest {
        val api = mockk<AuthApiService>(relaxed = true)
        val hasher = PasswordHasher()
        val enviado = slot<AuthCambiarPasswordRequestDto>()

        RemoteAuthRepository(api, hasher).cambiarPassword("usuario-1", "nueva-segura")

        coVerify { api.cambiarPassword(capture(enviado)) }
        assertTrue(enviado.captured.passwordHash.startsWith("$2"))
        assertTrue(hasher.verify("nueva-segura", enviado.captured.passwordHash))
    }
}
