package com.pdv.pos.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.local.LocalRolRepository
import com.pdv.pos.data.local.RolDao
import com.pdv.pos.data.local.RolEntity
import com.pdv.pos.data.remote.RemoteRolRepository
import com.pdv.pos.data.remote.RolApiService
import com.pdv.pos.data.remote.dto.RolDto
import com.pdv.pos.data.remote.dto.RolListResponseDto
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.Rol
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

class ModeAwareRolRepositoryTest {

    private class FakeRolDao(seed: List<RolEntity>) : RolDao {
        private val entities = seed.toMutableList()
        override fun observeAll(): Flow<List<RolEntity>> = MutableStateFlow(entities.toList())
        override suspend fun getRol(rolId: String): RolEntity? = entities.find { it.localId == rolId }
        override suspend fun getByNombre(nombre: String): RolEntity? = entities.find { it.nombre == nombre }
        override suspend fun count(): Int = entities.size
        override suspend fun insert(entity: RolEntity) {
            entities.add(entity)
        }
        override suspend fun insertAll(entities: List<RolEntity>) {
            this.entities.addAll(entities)
        }
        override suspend fun update(entity: RolEntity) {
            entities.removeAll { it.localId == entity.localId }
            entities.add(entity)
        }
    }

    private fun preferences(tempDir: File): ConfiguracionPreferences {
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { File(tempDir, "test.preferences_pb") })
        return ConfiguracionPreferences(dataStore)
    }

    private fun rolLocalEntity() = RolEntity(
        localId = "local-1",
        remoteId = null,
        nombre = "Rol local",
        modulosPermitidos = listOf("venta"),
        esSistema = false,
        updatedAt = 0,
        isSynced = false,
        deletedAt = null,
    )

    @Test
    fun `switching BackendMode in DataStore switches which repository observeRoles reads from`(@TempDir tempDir: File) = runTest {
        val localDao = FakeRolDao(listOf(rolLocalEntity()))
        val appLogger = mockk<AppLogger>()
        val api = mockk<RolApiService>()
        coEvery { api.getRoles() } returns RolListResponseDto(
            items = listOf(
                RolDto(
                    id = "remote-1",
                    nombre = "Rol remoto",
                    modulosPermitidos = listOf("caja"),
                    esSistema = false,
                    updatedAt = "2026-08-21T12:00:00Z",
                ),
            ),
            page = 1,
            pageSize = 20,
            total = 1,
        )

        val preferences = preferences(tempDir)
        val repository = ModeAwareRolRepository(
            local = LocalRolRepository(localDao, appLogger),
            remote = RemoteRolRepository(api, appLogger),
            preferences = preferences,
        )

        assertEquals("Rol local", repository.observeRoles().first().single().nombre)

        preferences.setBackendMode(BackendMode.REMOTO)

        assertEquals("Rol remoto", repository.observeRoles().first().single().nombre)
    }

    @Test
    fun `switching BackendMode in DataStore switches which repository handles crearRol`(@TempDir tempDir: File) = runTest {
        val dao = mockk<RolDao>()
        coEvery { dao.insert(any()) } returns Unit
        val appLogger = mockk<AppLogger>()
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val local = LocalRolRepository(dao, appLogger)

        val api = mockk<RolApiService>()
        coEvery { api.createRol(any()) } returns RolDto(
            id = "remote-1",
            nombre = "Cajero",
            modulosPermitidos = listOf("venta"),
            esSistema = false,
            updatedAt = "2026-08-21T12:00:00Z",
        )
        val remote = RemoteRolRepository(api, appLogger)

        val preferences = preferences(tempDir)
        val repository = ModeAwareRolRepository(local = local, remote = remote, preferences = preferences)
        val rol = Rol(id = "rol-1", nombre = "Cajero", modulosPermitidos = listOf("venta"))

        repository.crearRol(rol, sucursalId = "suc-1", actorUsuario = "admin")
        coVerify(exactly = 1) { dao.insert(any()) }
        coVerify(exactly = 0) { api.createRol(any()) }

        preferences.setBackendMode(BackendMode.REMOTO)
        repository.crearRol(rol, sucursalId = "suc-1", actorUsuario = "admin")

        coVerify(exactly = 1) { api.createRol(any()) }
        coVerify(exactly = 1) { dao.insert(any()) }
    }
}
