package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.RolCreateRequestDto
import com.pdv.pos.data.remote.dto.RolDto
import com.pdv.pos.data.remote.dto.RolListResponseDto
import com.pdv.pos.domain.model.Rol
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

class RemoteRolRepositoryTest {

    private fun rolDeEjemplo() = Rol(
        id = "rol-1",
        nombre = "Cajero",
        modulosPermitidos = listOf("venta", "caja"),
        esSistema = false,
    )

    @Test
    fun `observeRoles maps the API response to domain Rol`() = runTest {
        val api = mockk<RolApiService>()
        val appLogger = mockk<AppLogger>()
        coEvery { api.getRoles() } returns RolListResponseDto(
            items = listOf(
                RolDto(
                    id = "rol-1",
                    nombre = "Cajero",
                    modulosPermitidos = listOf("venta", "caja"),
                    esSistema = false,
                    updatedAt = "2026-08-21T12:00:00Z",
                ),
            ),
            page = 1,
            pageSize = 20,
            total = 1,
        )
        val repository = RemoteRolRepository(api, appLogger)

        val result = repository.observeRoles().first()

        assertEquals(listOf(rolDeEjemplo()), result)
    }

    @Test
    fun `crearRol posts the mapped create request DTO`() = runTest {
        val api = mockk<RolApiService>()
        val appLogger = mockk<AppLogger>()
        coEvery { api.createRol(any()) } returns RolDto(
            id = "rol-1",
            nombre = "Cajero",
            modulosPermitidos = listOf("venta", "caja"),
            esSistema = false,
            updatedAt = "2026-08-21T12:00:00Z",
        )
        val repository = RemoteRolRepository(api, appLogger)

        repository.crearRol(rolDeEjemplo(), sucursalId = "suc-1", actorUsuario = "admin")

        coVerify {
            api.createRol(
                RolCreateRequestDto(localId = "rol-1", nombre = "Cajero", modulosPermitidos = listOf("venta", "caja")),
            )
        }
        coVerify(exactly = 0) { appLogger.log(any(), any(), any(), any()) }
    }

    @Test
    fun `crearRol logs ERROR and rethrows on a timeout`() = runTest {
        val api = mockk<RolApiService>()
        val appLogger = mockk<AppLogger>()
        coEvery { api.createRol(any()) } throws SocketTimeoutException("timeout")
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = RemoteRolRepository(api, appLogger)

        assertFailsWith<IOException> {
            repository.crearRol(rolDeEjemplo(), sucursalId = "suc-1", actorUsuario = "admin")
        }

        coVerify { appLogger.log(LogType.ERROR, sucursalId = "suc-1", usuario = "admin", mensaje = any()) }
    }

    @Test
    fun `eliminarRol logs ERROR and rethrows on a network failure`() = runTest {
        val api = mockk<RolApiService>()
        val appLogger = mockk<AppLogger>()
        coEvery { api.deleteRol(any()) } throws IOException("sin conexion")
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = RemoteRolRepository(api, appLogger)

        assertFailsWith<IOException> {
            repository.eliminarRol("rol-1", sucursalId = "suc-1", actorUsuario = "admin")
        }

        coVerify { appLogger.log(LogType.ERROR, sucursalId = "suc-1", usuario = "admin", mensaje = any()) }
    }
}
