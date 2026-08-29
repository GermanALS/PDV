package com.pdv.pos.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.local.LocalSyncConflictRepository
import com.pdv.pos.data.local.SyncConflictDao
import com.pdv.pos.data.local.SyncConflictEntity
import com.pdv.pos.data.remote.RemoteSyncConflictRepository
import com.pdv.pos.data.remote.SyncConflictApiService
import com.pdv.pos.data.remote.dto.SyncConflictDto
import com.pdv.pos.data.remote.dto.SyncConflictListResponseDto
import com.pdv.pos.domain.model.BackendMode
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ModeAwareSyncConflictRepositoryTest {

    private class FakeSyncConflictDao(seed: List<SyncConflictEntity>) : SyncConflictDao {
        private val entities = seed.toMutableList()
        override suspend fun insert(entity: SyncConflictEntity) {
            entities.add(entity)
        }
        override suspend fun getAll(): List<SyncConflictEntity> = entities.toList()
        override fun observeAll(): Flow<List<SyncConflictEntity>> = MutableStateFlow(entities.toList())
    }

    private fun preferences(tempDir: File): ConfiguracionPreferences {
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { File(tempDir, "test.preferences_pb") })
        return ConfiguracionPreferences(dataStore)
    }

    private fun localEntity() = SyncConflictEntity(
        id = "local-conflict",
        entidad = "inventario",
        entidadLocalId = "e1",
        sucursalId = null,
        valorLocal = "{\"cantidad\":1}",
        valorRemoto = "{\"cantidad\":2}",
        valorResuelto = "{\"cantidad\":3}",
        politicaAplicada = "evento_aditivo",
        resueltoAutomaticamente = true,
        fechaDeteccion = 0L,
    )

    private fun remoteDto() = SyncConflictDto(
        id = "remote-conflict",
        entidad = "articulos",
        entidadLocalId = "e2",
        sucursalId = null,
        valorLocal = Json.parseToJsonElement("{\"precio\":\"1.00\"}"),
        valorRemoto = Json.parseToJsonElement("{\"precio\":\"2.00\"}"),
        valorResuelto = Json.parseToJsonElement("{\"precio\":\"2.00\"}"),
        politicaAplicada = "last_write_wins",
        resueltoAutomaticamente = true,
        fechaDeteccion = "2026-08-28T14:03:11Z",
    )

    private fun repository(tempDir: File, preferences: ConfiguracionPreferences): ModeAwareSyncConflictRepository {
        val api = mockk<SyncConflictApiService>()
        coEvery { api.getSyncConflicts(any(), any()) } returns SyncConflictListResponseDto(
            items = listOf(remoteDto()),
            page = 1,
            pageSize = 100,
            total = 1,
        )
        return ModeAwareSyncConflictRepository(
            local = LocalSyncConflictRepository(FakeSyncConflictDao(listOf(localEntity()))),
            remote = RemoteSyncConflictRepository(api),
            preferences = preferences,
        )
    }

    @Test
    fun `LOCAL reads conflicts from Room`(@TempDir tempDir: File) = runTest {
        val preferences = preferences(tempDir)
        val repository = repository(tempDir, preferences)

        assertEquals("local-conflict", repository.observeConflictos().first().single().id)
    }

    @Test
    fun `LOCAL_CON_SINCRONIZACION also reads conflicts from Room`(@TempDir tempDir: File) = runTest {
        val preferences = preferences(tempDir)
        preferences.setBackendMode(BackendMode.LOCAL_CON_SINCRONIZACION)
        val repository = repository(tempDir, preferences)

        assertEquals("local-conflict", repository.observeConflictos().first().single().id)
    }

    @Test
    fun `switching to REMOTO switches to the backend list`(@TempDir tempDir: File) = runTest {
        val preferences = preferences(tempDir)
        val repository = repository(tempDir, preferences)

        assertEquals("local-conflict", repository.observeConflictos().first().single().id)

        preferences.setBackendMode(BackendMode.REMOTO)

        assertEquals("remote-conflict", repository.observeConflictos().first().single().id)
    }
}
