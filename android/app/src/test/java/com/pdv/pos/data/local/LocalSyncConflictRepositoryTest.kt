package com.pdv.pos.data.local

import com.pdv.pos.domain.model.SyncConflict
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LocalSyncConflictRepositoryTest {

    private class FakeSyncConflictDao : SyncConflictDao {
        private val entities = mutableListOf<SyncConflictEntity>()
        private val state = MutableStateFlow<List<SyncConflictEntity>>(emptyList())

        override suspend fun insert(entity: SyncConflictEntity) {
            entities.add(entity)
            state.value = entities.toList()
        }

        override suspend fun getAll(): List<SyncConflictEntity> = entities.toList()
        override fun observeAll(): Flow<List<SyncConflictEntity>> = state
    }

    private fun entity(id: String, resueltoAutomaticamente: Boolean = true) = SyncConflictEntity(
        id = id,
        entidad = "inventario",
        entidadLocalId = "local-$id",
        sucursalId = "suc-1",
        valorLocal = "{\"cantidad\": 2}",
        valorRemoto = "{\"cantidad\": 3}",
        valorResuelto = "{\"cantidad\": 5}",
        politicaAplicada = "evento_aditivo",
        resueltoAutomaticamente = resueltoAutomaticamente,
        fechaDeteccion = 1000L,
    )

    @Test
    fun `maps entity fields to the domain SyncConflict`() = runTest {
        val dao = FakeSyncConflictDao()
        dao.insert(entity(id = "c1", resueltoAutomaticamente = false))
        val repository = LocalSyncConflictRepository(dao)

        val result = repository.observeConflictos().first()

        assertEquals(
            SyncConflict(
                id = "c1",
                entidad = "inventario",
                entidadLocalId = "local-c1",
                sucursalId = "suc-1",
                valorLocal = "{\"cantidad\": 2}",
                valorRemoto = "{\"cantidad\": 3}",
                valorResuelto = "{\"cantidad\": 5}",
                politicaAplicada = "evento_aditivo",
                resueltoAutomaticamente = false,
                fechaDeteccion = 1000L,
            ),
            result.single(),
        )
    }

    @Test
    fun `emits an empty list when there are no conflicts`() = runTest {
        val repository = LocalSyncConflictRepository(FakeSyncConflictDao())

        assertEquals(emptyList<SyncConflict>(), repository.observeConflictos().first())
    }

    @Test
    fun `emits every registered conflict`() = runTest {
        val dao = FakeSyncConflictDao()
        dao.insert(entity(id = "c1"))
        dao.insert(entity(id = "c2"))
        val repository = LocalSyncConflictRepository(dao)

        val result = repository.observeConflictos().first()

        assertEquals(listOf("c1", "c2"), result.map { it.id })
    }
}
