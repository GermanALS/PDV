package com.pdv.pos.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.local.LocalSucursalRepository
import com.pdv.pos.data.local.SucursalDao
import com.pdv.pos.data.local.SucursalEntity
import com.pdv.pos.data.remote.RemoteSucursalRepository
import com.pdv.pos.data.remote.SucursalApiService
import com.pdv.pos.data.remote.dto.SucursalDto
import com.pdv.pos.data.remote.dto.SucursalListResponseDto
import com.pdv.pos.domain.model.BackendMode
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ModeAwareSucursalRepositoryTest {

    private class FakeSucursalDao(seed: List<SucursalEntity>) : SucursalDao {
        private val entities = seed.toMutableList()
        override fun observeAll(): Flow<List<SucursalEntity>> = MutableStateFlow(entities.toList())
        override suspend fun count(): Int = entities.size
        override suspend fun insert(entity: SucursalEntity) {
            entities.add(entity)
        }
    }

    private fun preferences(tempDir: File): ConfiguracionPreferences {
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { File(tempDir, "test.preferences_pb") })
        return ConfiguracionPreferences(dataStore)
    }

    @Test
    fun `switching BackendMode in DataStore switches which repository is used`(@TempDir tempDir: File) = runTest {
        val localDao = FakeSucursalDao(
            listOf(
                SucursalEntity(
                    localId = "local-1",
                    remoteId = null,
                    nombre = "Sucursal Local",
                    direccion = null,
                    activa = true,
                    updatedAt = 0,
                    isSynced = false,
                    deletedAt = null,
                )
            )
        )
        val api = mockk<SucursalApiService>()
        coEvery { api.getSucursales() } returns SucursalListResponseDto(
            items = listOf(
                SucursalDto(
                    id = "remote-1",
                    nombre = "Sucursal Remota",
                    activa = true,
                    updatedAt = "2026-08-18T12:00:00Z",
                )
            ),
            page = 1,
            pageSize = 20,
            total = 1,
        )

        val preferences = preferences(tempDir)
        val repository = ModeAwareSucursalRepository(
            local = LocalSucursalRepository(localDao),
            remote = RemoteSucursalRepository(api),
            preferences = preferences,
        )

        assertEquals("Sucursal Local", repository.observeSucursales().first().single().nombre)

        preferences.setBackendMode(BackendMode.REMOTO)

        assertEquals("Sucursal Remota", repository.observeSucursales().first().single().nombre)
    }

    @Test
    fun `LOCAL_CON_SINCRONIZACION also reads the sucursal catalog from the backend`(@TempDir tempDir: File) = runTest {
        val localDao = FakeSucursalDao(
            listOf(
                SucursalEntity(
                    localId = "local-1",
                    remoteId = null,
                    nombre = "Sucursal Local",
                    direccion = null,
                    activa = true,
                    updatedAt = 0,
                    isSynced = false,
                    deletedAt = null,
                )
            )
        )
        val api = mockk<SucursalApiService>()
        coEvery { api.getSucursales() } returns SucursalListResponseDto(
            items = listOf(
                SucursalDto(
                    id = "remote-1",
                    nombre = "Sucursal Remota",
                    activa = true,
                    updatedAt = "2026-08-18T12:00:00Z",
                )
            ),
            page = 1,
            pageSize = 20,
            total = 1,
        )

        val preferences = preferences(tempDir)
        preferences.setBackendMode(BackendMode.LOCAL_CON_SINCRONIZACION)
        val repository = ModeAwareSucursalRepository(
            local = LocalSucursalRepository(localDao),
            remote = RemoteSucursalRepository(api),
            preferences = preferences,
        )

        assertEquals("Sucursal Remota", repository.observeSucursales().first().single().nombre)
    }
}
