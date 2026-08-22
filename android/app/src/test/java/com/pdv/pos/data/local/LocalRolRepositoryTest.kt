package com.pdv.pos.data.local

import com.pdv.pos.domain.model.Rol
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class LocalRolRepositoryTest {

    private class FakeRolDao : RolDao {
        private val entities = mutableListOf<RolEntity>()
        private val state = MutableStateFlow<List<RolEntity>>(emptyList())

        override fun observeAll(): Flow<List<RolEntity>> = state
        override suspend fun getRol(rolId: String): RolEntity? = entities.find { it.localId == rolId }
        override suspend fun getByNombre(nombre: String): RolEntity? = entities.find { it.nombre == nombre }
        override suspend fun count(): Int = entities.size
        override suspend fun insert(entity: RolEntity) {
            entities.add(entity)
            state.value = entities.toList()
        }
        override suspend fun insertAll(entities: List<RolEntity>) {
            this.entities.addAll(entities)
            state.value = this.entities.toList()
        }
        override suspend fun update(entity: RolEntity) {
            entities.removeAll { it.localId == entity.localId }
            entities.add(entity)
            state.value = entities.toList()
        }
    }

    // Cierra el hallazgo del code-reviewer en el sub-paso 1: sin este seed,
    // un dispositivo en modo LOCAL que nunca sincronizo con el backend
    // arranca con `roles` vacia y el modulo Usuarios queda inutilizable.
    @Test
    fun `observeRoles seeds the 2 roles de sistema when Room starts empty`() = runTest {
        val dao = FakeRolDao()
        val repository = LocalRolRepository(dao, mockk())

        val result = repository.observeRoles().first()

        assertEquals(2, result.size)
        assertTrue(result.all { it.esSistema })
        assertEquals(setOf("administrador", "encargado_turno"), result.map { it.nombre }.toSet())
        val administrador = result.single { it.nombre == "administrador" }
        assertTrue("usuarios" in administrador.modulosPermitidos && "configuracion" in administrador.modulosPermitidos)
        val encargadoTurno = result.single { it.nombre == "encargado_turno" }
        assertTrue("usuarios" !in encargadoTurno.modulosPermitidos && "configuracion" !in encargadoTurno.modulosPermitidos)
    }

    @Test
    fun `observeRoles does not duplicate the seed when roles already exist`() = runTest {
        val dao = FakeRolDao()
        dao.insert(
            RolEntity(
                localId = "existente",
                remoteId = "remote-1",
                nombre = "rol_personalizado",
                modulosPermitidos = listOf("venta"),
                esSistema = false,
                updatedAt = 0,
                isSynced = true,
                deletedAt = null,
            ),
        )
        val repository = LocalRolRepository(dao, mockk())

        val result = repository.observeRoles().first()

        assertEquals(1, result.size)
        assertEquals("rol_personalizado", result.single().nombre)
    }

    private fun rolDeEjemplo() = Rol(
        id = "rol-1",
        nombre = "Cajero",
        modulosPermitidos = listOf("venta", "caja"),
        esSistema = false,
    )

    private fun entityDeEjemplo(esSistema: Boolean = false) = RolEntity(
        localId = "rol-1",
        remoteId = null,
        nombre = "Cajero",
        modulosPermitidos = listOf("venta", "caja"),
        esSistema = esSistema,
        updatedAt = 1_700_000_000_000L,
        isSynced = true,
        deletedAt = null,
    )

    @Test
    fun `crearRol inserts entity and logs DB_WRITE`() = runTest {
        val dao = mockk<RolDao>()
        val appLogger = mockk<AppLogger>()
        coEvery { dao.insert(any()) } returns Unit
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = LocalRolRepository(dao, appLogger)

        repository.crearRol(rolDeEjemplo(), sucursalId = "suc-1", actorUsuario = "admin")

        coVerify {
            dao.insert(match { it.localId == "rol-1" && it.nombre == "Cajero" && !it.esSistema })
        }
        coVerify { appLogger.log(LogType.DB_WRITE, sucursalId = "suc-1", usuario = "admin", mensaje = any()) }
    }

    @Test
    fun `crearRol propagates a DAO failure without logging DB_WRITE`() = runTest {
        val dao = mockk<RolDao>()
        val appLogger = mockk<AppLogger>()
        coEvery { dao.insert(any()) } throws IllegalStateException("nombre duplicado")
        val repository = LocalRolRepository(dao, appLogger)

        assertFailsWith<IllegalStateException> {
            repository.crearRol(rolDeEjemplo(), sucursalId = "suc-1", actorUsuario = "admin")
        }

        coVerify(exactly = 0) { appLogger.log(any(), any(), any(), any()) }
    }

    @Test
    fun `actualizarRol updates existing entity and logs DB_WRITE`() = runTest {
        val dao = mockk<RolDao>()
        val appLogger = mockk<AppLogger>()
        coEvery { dao.getRol("rol-1") } returns entityDeEjemplo()
        coEvery { dao.update(any()) } returns Unit
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = LocalRolRepository(dao, appLogger)
        val editado = rolDeEjemplo().copy(nombre = "Cajero senior", modulosPermitidos = listOf("venta"))

        repository.actualizarRol(editado, sucursalId = "suc-1", actorUsuario = "admin")

        coVerify {
            dao.update(match { it.nombre == "Cajero senior" && it.modulosPermitidos == listOf("venta") })
        }
        coVerify { appLogger.log(LogType.DB_WRITE, sucursalId = "suc-1", usuario = "admin", mensaje = any()) }
    }

    @Test
    fun `actualizarRol fails when rol does not exist`() = runTest {
        val dao = mockk<RolDao>()
        val appLogger = mockk<AppLogger>()
        coEvery { dao.getRol("rol-1") } returns null
        val repository = LocalRolRepository(dao, appLogger)

        assertFailsWith<IllegalStateException> {
            repository.actualizarRol(rolDeEjemplo(), sucursalId = "suc-1", actorUsuario = "admin")
        }

        coVerify(exactly = 0) { appLogger.log(any(), any(), any(), any()) }
    }

    @Test
    fun `actualizarRol fails when rol is de sistema`() = runTest {
        val dao = mockk<RolDao>()
        val appLogger = mockk<AppLogger>()
        coEvery { dao.getRol("rol-1") } returns entityDeEjemplo(esSistema = true)
        val repository = LocalRolRepository(dao, appLogger)

        assertFailsWith<IllegalStateException> {
            repository.actualizarRol(rolDeEjemplo(), sucursalId = "suc-1", actorUsuario = "admin")
        }

        coVerify(exactly = 0) { dao.update(any()) }
        coVerify(exactly = 0) { appLogger.log(any(), any(), any(), any()) }
    }

    @Test
    fun `eliminarRol soft-deletes entity and logs DB_WRITE`() = runTest {
        val dao = mockk<RolDao>()
        val appLogger = mockk<AppLogger>()
        coEvery { dao.getRol("rol-1") } returns entityDeEjemplo()
        coEvery { dao.update(any()) } returns Unit
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = LocalRolRepository(dao, appLogger)

        repository.eliminarRol("rol-1", sucursalId = "suc-1", actorUsuario = "admin")

        coVerify { dao.update(match { it.deletedAt != null }) }
        coVerify { appLogger.log(LogType.DB_WRITE, sucursalId = "suc-1", usuario = "admin", mensaje = any()) }
    }

    @Test
    fun `eliminarRol fails when rol is de sistema`() = runTest {
        val dao = mockk<RolDao>()
        val appLogger = mockk<AppLogger>()
        coEvery { dao.getRol("rol-1") } returns entityDeEjemplo(esSistema = true)
        val repository = LocalRolRepository(dao, appLogger)

        assertFailsWith<IllegalStateException> {
            repository.eliminarRol("rol-1", sucursalId = "suc-1", actorUsuario = "admin")
        }

        coVerify(exactly = 0) { dao.update(any()) }
        coVerify(exactly = 0) { appLogger.log(any(), any(), any(), any()) }
    }
}
