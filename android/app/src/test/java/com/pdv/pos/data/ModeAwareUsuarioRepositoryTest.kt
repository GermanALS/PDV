package com.pdv.pos.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.local.LocalUsuarioRepository
import com.pdv.pos.data.local.UsuarioDao
import com.pdv.pos.data.local.UsuarioEntity
import com.pdv.pos.data.remote.RemoteUsuarioRepository
import com.pdv.pos.data.remote.UsuarioApiService
import com.pdv.pos.data.remote.dto.UsuarioDto
import com.pdv.pos.data.remote.dto.UsuarioListResponseDto
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.Usuario
import com.pdv.pos.logging.AppLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ModeAwareUsuarioRepositoryTest {

    private class FakeUsuarioDao(seed: List<UsuarioEntity>) : UsuarioDao {
        private val entities = seed.toMutableList()
        override fun observeAll(): Flow<List<UsuarioEntity>> = MutableStateFlow(entities.toList())
        override suspend fun getUsuario(usuarioId: String): UsuarioEntity? = entities.find { it.localId == usuarioId }
        override suspend fun getByUsername(username: String): UsuarioEntity? = entities.find { it.username == username }
        override suspend fun count(): Int = entities.size
        override suspend fun insert(entity: UsuarioEntity) {
            entities.add(entity)
        }
        override suspend fun update(entity: UsuarioEntity) {
            entities.removeAll { it.localId == entity.localId }
            entities.add(entity)
        }
    }

    private fun preferences(tempDir: File): ConfiguracionPreferences {
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { File(tempDir, "test.preferences_pb") })
        return ConfiguracionPreferences(dataStore)
    }

    private fun usuarioLocalEntity() = UsuarioEntity(
        localId = "local-1",
        remoteId = null,
        username = "usuario_local",
        nombreCompleto = "Usuario Local",
        passwordHash = null,
        rolId = "rol-encargado-turno",
        activo = true,
        updatedAt = 0,
        isSynced = false,
        deletedAt = null,
    )

    @Test
    fun `switching BackendMode in DataStore switches which repository observeUsuarios reads from`(@TempDir tempDir: File) = runTest {
        val localDao = FakeUsuarioDao(listOf(usuarioLocalEntity()))
        val appLogger = mockk<AppLogger>()
        val api = mockk<UsuarioApiService>()
        coEvery { api.getUsuarios() } returns UsuarioListResponseDto(
            items = listOf(
                UsuarioDto(
                    id = "remote-1",
                    username = "usuario_remoto",
                    nombreCompleto = "Usuario Remoto",
                    rolId = "rol-administrador",
                    activo = true,
                    updatedAt = "2026-08-21T12:00:00Z",
                ),
            ),
            page = 1,
            pageSize = 20,
            total = 1,
        )

        val preferences = preferences(tempDir)
        val repository = ModeAwareUsuarioRepository(
            local = LocalUsuarioRepository(localDao, appLogger),
            remote = RemoteUsuarioRepository(api, appLogger),
            preferences = preferences,
        )

        assertEquals("usuario_local", repository.observeUsuarios().first().single().username)

        preferences.setBackendMode(BackendMode.REMOTO)

        assertEquals("usuario_remoto", repository.observeUsuarios().first().single().username)
    }

    @Test
    fun `switching BackendMode in DataStore switches which repository handles crearUsuario`(@TempDir tempDir: File) = runTest {
        val dao = mockk<UsuarioDao>()
        coEvery { dao.insert(any()) } returns Unit
        val appLogger = mockk<AppLogger>()
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val local = LocalUsuarioRepository(dao, appLogger)

        val api = mockk<UsuarioApiService>()
        coEvery { api.createUsuario(any()) } returns UsuarioDto(
            id = "remote-1",
            username = "encargado1",
            nombreCompleto = "Encargado de Turno",
            rolId = "rol-encargado-turno",
            activo = true,
            updatedAt = "2026-08-21T12:00:00Z",
        )
        val remote = RemoteUsuarioRepository(api, appLogger)

        val preferences = preferences(tempDir)
        val repository = ModeAwareUsuarioRepository(local = local, remote = remote, preferences = preferences)
        val usuario = Usuario(
            id = "usuario-1",
            username = "encargado1",
            nombreCompleto = "Encargado de Turno",
            rolId = "rol-encargado-turno",
            activo = true,
        )

        repository.crearUsuario(usuario, passwordHash = null, sucursalId = "suc-1", actorUsuario = "admin")
        coVerify(exactly = 1) { dao.insert(any()) }
        coVerify(exactly = 0) { api.createUsuario(any()) }

        preferences.setBackendMode(BackendMode.REMOTO)
        repository.crearUsuario(usuario, passwordHash = null, sucursalId = "suc-1", actorUsuario = "admin")

        coVerify(exactly = 1) { api.createUsuario(any()) }
        coVerify(exactly = 1) { dao.insert(any()) }
    }
}
