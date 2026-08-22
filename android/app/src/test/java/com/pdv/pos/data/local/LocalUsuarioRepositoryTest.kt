package com.pdv.pos.data.local

import com.pdv.pos.domain.model.Usuario
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class LocalUsuarioRepositoryTest {

    private fun usuarioDeEjemplo() = Usuario(
        id = "usuario-1",
        username = "encargado1",
        nombreCompleto = "Encargado de Turno",
        rolId = "rol-encargado-turno",
        activo = true,
    )

    private fun entityDeEjemplo() = UsuarioEntity(
        localId = "usuario-1",
        remoteId = null,
        username = "encargado1",
        nombreCompleto = "Encargado de Turno",
        passwordHash = null,
        rolId = "rol-encargado-turno",
        activo = true,
        updatedAt = 1_700_000_000_000L,
        isSynced = true,
        deletedAt = null,
    )

    @Test
    fun `crearUsuario inserts entity and logs DB_WRITE`() = runTest {
        val dao = mockk<UsuarioDao>()
        val appLogger = mockk<AppLogger>()
        coEvery { dao.insert(any()) } returns Unit
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = LocalUsuarioRepository(dao, appLogger)

        repository.crearUsuario(usuarioDeEjemplo(), passwordHash = null, sucursalId = "suc-1", actorUsuario = "admin")

        coVerify {
            dao.insert(match { it.localId == "usuario-1" && it.username == "encargado1" && it.passwordHash == null })
        }
        coVerify { appLogger.log(LogType.DB_WRITE, sucursalId = "suc-1", usuario = "admin", mensaje = any()) }
    }

    @Test
    fun `crearUsuario stores the given passwordHash`() = runTest {
        val dao = mockk<UsuarioDao>()
        val appLogger = mockk<AppLogger>()
        coEvery { dao.insert(any()) } returns Unit
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = LocalUsuarioRepository(dao, appLogger)

        repository.crearUsuario(usuarioDeEjemplo(), passwordHash = "hash-bcrypt", sucursalId = "suc-1", actorUsuario = "admin")

        coVerify { dao.insert(match { it.passwordHash == "hash-bcrypt" }) }
    }

    @Test
    fun `crearUsuario propagates a DAO failure without logging DB_WRITE`() = runTest {
        val dao = mockk<UsuarioDao>()
        val appLogger = mockk<AppLogger>()
        coEvery { dao.insert(any()) } throws IllegalStateException("username duplicado")
        val repository = LocalUsuarioRepository(dao, appLogger)

        assertFailsWith<IllegalStateException> {
            repository.crearUsuario(usuarioDeEjemplo(), passwordHash = null, sucursalId = "suc-1", actorUsuario = "admin")
        }

        coVerify(exactly = 0) { appLogger.log(any(), any(), any(), any()) }
    }

    @Test
    fun `actualizarUsuario preserves the existing passwordHash when none is given`() = runTest {
        val dao = mockk<UsuarioDao>()
        val appLogger = mockk<AppLogger>()
        coEvery { dao.getUsuario("usuario-1") } returns entityDeEjemplo().copy(passwordHash = "hash-existente")
        coEvery { dao.update(any()) } returns Unit
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = LocalUsuarioRepository(dao, appLogger)
        val editado = usuarioDeEjemplo().copy(nombreCompleto = "Encargado Editado", activo = false)

        repository.actualizarUsuario(editado, passwordHash = null, sucursalId = "suc-1", actorUsuario = "admin")

        coVerify {
            dao.update(
                match {
                    it.nombreCompleto == "Encargado Editado" && !it.activo && it.username == "encargado1" &&
                        it.passwordHash == "hash-existente"
                },
            )
        }
        coVerify { appLogger.log(LogType.DB_WRITE, sucursalId = "suc-1", usuario = "admin", mensaje = any()) }
    }

    @Test
    fun `actualizarUsuario overwrites the passwordHash when a new one is given`() = runTest {
        val dao = mockk<UsuarioDao>()
        val appLogger = mockk<AppLogger>()
        coEvery { dao.getUsuario("usuario-1") } returns entityDeEjemplo().copy(passwordHash = "hash-viejo")
        coEvery { dao.update(any()) } returns Unit
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = LocalUsuarioRepository(dao, appLogger)

        repository.actualizarUsuario(usuarioDeEjemplo(), passwordHash = "hash-nuevo", sucursalId = "suc-1", actorUsuario = "admin")

        coVerify { dao.update(match { it.passwordHash == "hash-nuevo" }) }
    }

    @Test
    fun `actualizarUsuario fails when usuario does not exist`() = runTest {
        val dao = mockk<UsuarioDao>()
        val appLogger = mockk<AppLogger>()
        coEvery { dao.getUsuario("usuario-1") } returns null
        val repository = LocalUsuarioRepository(dao, appLogger)

        assertFailsWith<IllegalStateException> {
            repository.actualizarUsuario(usuarioDeEjemplo(), passwordHash = null, sucursalId = "suc-1", actorUsuario = "admin")
        }

        coVerify(exactly = 0) { appLogger.log(any(), any(), any(), any()) }
    }

    @Test
    fun `eliminarUsuario soft-deletes entity and logs DB_WRITE`() = runTest {
        val dao = mockk<UsuarioDao>()
        val appLogger = mockk<AppLogger>()
        coEvery { dao.getUsuario("usuario-1") } returns entityDeEjemplo()
        coEvery { dao.update(any()) } returns Unit
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = LocalUsuarioRepository(dao, appLogger)

        repository.eliminarUsuario("usuario-1", sucursalId = "suc-1", actorUsuario = "admin")

        coVerify { dao.update(match { it.deletedAt != null }) }
        coVerify { appLogger.log(LogType.DB_WRITE, sucursalId = "suc-1", usuario = "admin", mensaje = any()) }
    }
}
