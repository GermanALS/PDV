package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.UsuarioCreateRequestDto
import com.pdv.pos.data.remote.dto.UsuarioDto
import com.pdv.pos.data.remote.dto.UsuarioListResponseDto
import com.pdv.pos.domain.model.Usuario
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.test.assertFailsWith

class RemoteUsuarioRepositoryTest {

    private fun usuarioDeEjemplo() = Usuario(
        id = "usuario-1",
        username = "encargado1",
        nombreCompleto = "Encargado de Turno",
        rol = "encargado_turno",
        activo = true,
    )

    @Test
    fun `observeUsuarios maps the API response to domain Usuario`() = runTest {
        val api = mockk<UsuarioApiService>()
        val appLogger = mockk<AppLogger>()
        coEvery { api.getUsuarios() } returns UsuarioListResponseDto(
            items = listOf(
                UsuarioDto(
                    id = "usuario-1",
                    username = "encargado1",
                    nombreCompleto = "Encargado de Turno",
                    rol = "encargado_turno",
                    activo = true,
                    updatedAt = "2026-08-21T12:00:00Z",
                ),
            ),
            page = 1,
            pageSize = 20,
            total = 1,
        )
        val repository = RemoteUsuarioRepository(api, appLogger)

        val result = repository.observeUsuarios().first()

        assertEquals(listOf(usuarioDeEjemplo()), result)
    }

    @Test
    fun `crearUsuario posts the mapped create request DTO`() = runTest {
        val api = mockk<UsuarioApiService>()
        val appLogger = mockk<AppLogger>()
        coEvery { api.createUsuario(any()) } returns UsuarioDto(
            id = "usuario-1",
            username = "encargado1",
            nombreCompleto = "Encargado de Turno",
            rol = "encargado_turno",
            activo = true,
            updatedAt = "2026-08-21T12:00:00Z",
        )
        val repository = RemoteUsuarioRepository(api, appLogger)

        repository.crearUsuario(usuarioDeEjemplo(), sucursalId = "suc-1", actorUsuario = "admin")

        coVerify {
            api.createUsuario(
                UsuarioCreateRequestDto(
                    localId = "usuario-1",
                    username = "encargado1",
                    nombreCompleto = "Encargado de Turno",
                    rol = "encargado_turno",
                    activo = true,
                ),
            )
        }
        coVerify(exactly = 0) { appLogger.log(any(), any(), any(), any()) }
    }

    @Test
    fun `crearUsuario logs ERROR and rethrows on a timeout`() = runTest {
        val api = mockk<UsuarioApiService>()
        val appLogger = mockk<AppLogger>()
        coEvery { api.createUsuario(any()) } throws SocketTimeoutException("timeout")
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = RemoteUsuarioRepository(api, appLogger)

        assertFailsWith<IOException> {
            repository.crearUsuario(usuarioDeEjemplo(), sucursalId = "suc-1", actorUsuario = "admin")
        }

        coVerify { appLogger.log(LogType.ERROR, sucursalId = "suc-1", usuario = "admin", mensaje = any()) }
    }

    @Test
    fun `eliminarUsuario logs ERROR and rethrows on a network failure`() = runTest {
        val api = mockk<UsuarioApiService>()
        val appLogger = mockk<AppLogger>()
        coEvery { api.deleteUsuario(any()) } throws IOException("sin conexion")
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = RemoteUsuarioRepository(api, appLogger)

        assertFailsWith<IOException> {
            repository.eliminarUsuario("usuario-1", sucursalId = "suc-1", actorUsuario = "admin")
        }

        coVerify { appLogger.log(LogType.ERROR, sucursalId = "suc-1", usuario = "admin", mensaje = any()) }
    }
}
