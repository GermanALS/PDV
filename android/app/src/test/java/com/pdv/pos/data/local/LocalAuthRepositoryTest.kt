package com.pdv.pos.data.local

import com.pdv.pos.auth.PasswordHasher
import com.pdv.pos.domain.repository.LoginResultado
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalAuthRepositoryTest {

    private class FakeUsuarioDao : UsuarioDao {
        val entities = mutableListOf<UsuarioEntity>()
        override fun observeAll(): Flow<List<UsuarioEntity>> = MutableStateFlow(entities.toList())
        override suspend fun getUsuario(usuarioId: String): UsuarioEntity? = entities.find { it.localId == usuarioId }
        override suspend fun getByUsername(username: String): UsuarioEntity? = entities.find { it.username == username }
        override suspend fun count(): Int = entities.size
        override suspend fun insert(entity: UsuarioEntity) { entities.add(entity) }
        override suspend fun update(entity: UsuarioEntity) {
            entities.removeAll { it.localId == entity.localId }
            entities.add(entity)
        }
    }

    private class FakeRolDao : RolDao {
        val entities = mutableListOf<RolEntity>()
        override fun observeAll(): Flow<List<RolEntity>> = MutableStateFlow(entities.toList())
        override suspend fun getRol(rolId: String): RolEntity? = entities.find { it.localId == rolId }
        override suspend fun getByNombre(nombre: String): RolEntity? = entities.find { it.nombre == nombre }
        override suspend fun count(): Int = entities.size
        override suspend fun insert(entity: RolEntity) { entities.add(entity) }
        override suspend fun insertAll(entities: List<RolEntity>) { this.entities.addAll(entities) }
        override suspend fun update(entity: RolEntity) {
            entities.removeAll { it.localId == entity.localId }
            entities.add(entity)
        }
    }

    private val passwordHasher = PasswordHasher()

    @Test
    fun `login bootstraps the default admin usuario when Room is empty and accepts it`() = runTest {
        val usuarioDao = FakeUsuarioDao()
        val rolDao = FakeRolDao()
        val repository = LocalAuthRepository(usuarioDao, rolDao, passwordHasher)

        val resultado = repository.login("admin", "admin123")

        assertTrue(resultado is LoginResultado.Exitoso)
        val exitoso = resultado as LoginResultado.Exitoso
        assertEquals("admin", exitoso.usuario.username)
        assertTrue(rolDao.entities.any { it.nombre == "administrador" })
    }

    @Test
    fun `login does not duplicate the bootstrap admin on a second attempt`() = runTest {
        val usuarioDao = FakeUsuarioDao()
        val rolDao = FakeRolDao()
        val repository = LocalAuthRepository(usuarioDao, rolDao, passwordHasher)

        repository.login("admin", "admin123")
        repository.login("admin", "admin123")

        assertEquals(1, usuarioDao.entities.size)
    }

    @Test
    fun `login with the wrong password returns CredencialesInvalidas`() = runTest {
        val usuarioDao = FakeUsuarioDao()
        val rolDao = FakeRolDao()
        val repository = LocalAuthRepository(usuarioDao, rolDao, passwordHasher)

        val resultado = repository.login("admin", "contrasena-incorrecta")

        assertEquals(LoginResultado.CredencialesInvalidas, resultado)
    }

    @Test
    fun `login with a nonexistent username returns CredencialesInvalidas`() = runTest {
        val usuarioDao = FakeUsuarioDao()
        val rolDao = FakeRolDao()
        val repository = LocalAuthRepository(usuarioDao, rolDao, passwordHasher)

        val resultado = repository.login("no_existe", "cualquiera")

        assertEquals(LoginResultado.CredencialesInvalidas, resultado)
    }

    @Test
    fun `login with an inactive usuario returns CredencialesInvalidas`() = runTest {
        val usuarioDao = FakeUsuarioDao()
        val rolDao = FakeRolDao()
        rolDao.entities.add(
            RolEntity(
                localId = "rol-1",
                remoteId = null,
                nombre = "encargado_turno",
                modulosPermitidos = listOf("venta"),
                esSistema = true,
                updatedAt = 0,
                isSynced = true,
                deletedAt = null,
            ),
        )
        usuarioDao.entities.add(
            UsuarioEntity(
                localId = "usuario-1",
                remoteId = null,
                username = "inactivo",
                nombreCompleto = "Usuario Inactivo",
                passwordHash = passwordHasher.hash("secret123"),
                rolId = "rol-1",
                activo = false,
                updatedAt = 0,
                isSynced = true,
                deletedAt = null,
            ),
        )
        val repository = LocalAuthRepository(usuarioDao, rolDao, passwordHasher)

        val resultado = repository.login("inactivo", "secret123")

        assertEquals(LoginResultado.CredencialesInvalidas, resultado)
    }

    @Test
    fun `login with a usuario without a password assigned returns CredencialesInvalidas`() = runTest {
        val usuarioDao = FakeUsuarioDao()
        val rolDao = FakeRolDao()
        rolDao.entities.add(
            RolEntity(
                localId = "rol-1",
                remoteId = null,
                nombre = "encargado_turno",
                modulosPermitidos = listOf("venta"),
                esSistema = true,
                updatedAt = 0,
                isSynced = true,
                deletedAt = null,
            ),
        )
        usuarioDao.entities.add(
            UsuarioEntity(
                localId = "usuario-1",
                remoteId = null,
                username = "sin_password",
                nombreCompleto = "Sin Password",
                passwordHash = null,
                rolId = "rol-1",
                activo = true,
                updatedAt = 0,
                isSynced = true,
                deletedAt = null,
            ),
        )
        val repository = LocalAuthRepository(usuarioDao, rolDao, passwordHasher)

        val resultado = repository.login("sin_password", "cualquiera")

        assertEquals(LoginResultado.CredencialesInvalidas, resultado)
    }
}
