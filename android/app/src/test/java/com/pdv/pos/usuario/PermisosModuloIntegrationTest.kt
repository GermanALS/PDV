package com.pdv.pos.usuario

import com.pdv.pos.data.local.LocalRolRepository
import com.pdv.pos.data.local.LocalUsuarioRepository
import com.pdv.pos.data.local.RolDao
import com.pdv.pos.data.local.RolEntity
import com.pdv.pos.data.local.UsuarioDao
import com.pdv.pos.data.local.UsuarioEntity
import com.pdv.pos.domain.model.modulosPermitidos
import com.pdv.pos.logging.AppLogger
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Cierra "Permisos por rol y activacion/desactivacion de modulos segun esos
// permisos" (PLAN.md Parte 13, checklist "Roles y permisos"): confirma, con
// LocalUsuarioRepository y LocalRolRepository reales (no mocks del calculo
// de permisos), que un usuario con un rol restringido no obtiene los
// modulos que ese rol no lista - la parte de UI (HelloScreen filtrando
// botones contra la sesion real) llega en el sub-paso 2, cuando el login
// real reemplaza al SessionManager ficticio y ya trae el usuario logueado.
class PermisosModuloIntegrationTest {

    private class FakeUsuarioDao(seed: List<UsuarioEntity>) : UsuarioDao {
        private val entities = seed.toMutableList()
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

    private class FakeRolDao(seed: List<RolEntity>) : RolDao {
        private val entities = seed.toMutableList()
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

    private fun rolEntity(id: String, nombre: String, modulos: List<String>) = RolEntity(
        localId = id,
        remoteId = null,
        nombre = nombre,
        modulosPermitidos = modulos,
        esSistema = true,
        updatedAt = 0,
        isSynced = true,
        deletedAt = null,
    )

    private fun usuarioEntity(id: String, username: String, rolId: String) = UsuarioEntity(
        localId = id,
        remoteId = null,
        username = username,
        nombreCompleto = username,
        passwordHash = null,
        rolId = rolId,
        activo = true,
        updatedAt = 0,
        isSynced = true,
        deletedAt = null,
    )

    @Test
    fun `un usuario con rol restringido no obtiene los modulos que ese rol no permite`() = runTest {
        val appLogger = mockk<AppLogger>()
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit

        val rolAdministrador = rolEntity(
            "rol-admin",
            "administrador",
            listOf("venta", "entrada", "inventario", "caja", "devoluciones", "usuarios", "configuracion"),
        )
        val rolEncargadoTurno = rolEntity(
            "rol-encargado",
            "encargado_turno",
            listOf("venta", "entrada", "inventario", "caja", "devoluciones"),
        )
        val rolRepository = LocalRolRepository(
            FakeRolDao(listOf(rolAdministrador, rolEncargadoTurno)),
            appLogger,
        )
        val usuarioRepository = LocalUsuarioRepository(
            FakeUsuarioDao(
                listOf(
                    usuarioEntity("usuario-admin", "admin", "rol-admin"),
                    usuarioEntity("usuario-encargado", "user1", "rol-encargado"),
                ),
            ),
            appLogger,
        )

        val roles = rolRepository.observeRoles().first()
        val usuarios = usuarioRepository.observeUsuarios().first()
        val admin = usuarios.single { it.username == "admin" }
        val encargado = usuarios.single { it.username == "user1" }

        val modulosAdmin = admin.modulosPermitidos(roles)
        val modulosEncargado = encargado.modulosPermitidos(roles)

        assertTrue("usuarios" in modulosAdmin)
        assertTrue("configuracion" in modulosAdmin)
        assertFalse("usuarios" in modulosEncargado)
        assertFalse("configuracion" in modulosEncargado)
        assertTrue("venta" in modulosEncargado)
    }
}
