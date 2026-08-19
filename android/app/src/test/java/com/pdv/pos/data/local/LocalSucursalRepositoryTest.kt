package com.pdv.pos.data.local

import com.pdv.pos.domain.model.Sucursal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LocalSucursalRepositoryTest {

    private class FakeSucursalDao : SucursalDao {
        private val entities = mutableListOf<SucursalEntity>()
        private val state = MutableStateFlow<List<SucursalEntity>>(emptyList())

        override fun observeAll(): Flow<List<SucursalEntity>> = state
        override suspend fun count(): Int = entities.size
        override suspend fun insert(entity: SucursalEntity) {
            entities.add(entity)
            state.value = entities.toList()
        }
    }

    @Test
    fun `creates exactly one default sucursal with a null remoteId when Room starts empty`() = runTest {
        val dao = FakeSucursalDao()
        val repository = LocalSucursalRepository(dao)

        val result = repository.observeSucursales().first()
        val insertedEntity = dao.observeAll().first().single()

        assertEquals(1, result.size)
        assertEquals("Sucursal principal", result.single().nombre)
        assertNull(insertedEntity.remoteId)
    }

    @Test
    fun `does not duplicate the default sucursal when one already exists`() = runTest {
        val dao = FakeSucursalDao()
        dao.insert(
            SucursalEntity(
                localId = "existing",
                remoteId = "remote-1",
                nombre = "Sucursal Centro",
                direccion = null,
                activa = true,
                updatedAt = 0,
                isSynced = true,
                deletedAt = null,
            )
        )
        val repository = LocalSucursalRepository(dao)

        val result = repository.observeSucursales().first()

        assertEquals(1, result.size)
        assertEquals("Sucursal Centro", result.single().nombre)
    }

    @Test
    fun `maps entity fields to the domain Sucursal`() = runTest {
        val dao = FakeSucursalDao()
        dao.insert(
            SucursalEntity(
                localId = "abc",
                remoteId = null,
                nombre = "Sucursal Norte",
                direccion = "Av. Principal 123",
                activa = false,
                updatedAt = 0,
                isSynced = false,
                deletedAt = null,
            )
        )
        val repository = LocalSucursalRepository(dao)

        val result = repository.observeSucursales().first()

        assertEquals(
            Sucursal(id = "abc", nombre = "Sucursal Norte", direccion = "Av. Principal 123", activa = false),
            result.single(),
        )
    }
}
