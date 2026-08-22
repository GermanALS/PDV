package com.pdv.pos.data.local

import com.pdv.pos.auth.PasswordHasher
import com.pdv.pos.domain.model.Usuario
import com.pdv.pos.domain.repository.AuthRepository
import com.pdv.pos.domain.repository.LoginResultado
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// Contrasena de bootstrap de desarrollo, mismo valor que
// backend/alembic/versions/0009_seed_admin_usuario.py: un dispositivo en
// modo LOCAL que nunca sincroniza con el backend necesita el mismo punto de
// entrada, o nunca podria llegar a la pantalla de Usuarios para crear el
// primer administrador real.
private const val ADMIN_BOOTSTRAP_USERNAME = "admin"
private const val ADMIN_BOOTSTRAP_PASSWORD = "admin123"

@Singleton
class LocalAuthRepository @Inject constructor(
    private val usuarioDao: UsuarioDao,
    private val rolDao: RolDao,
    private val passwordHasher: PasswordHasher,
) : AuthRepository {

    override suspend fun login(username: String, password: String): LoginResultado {
        ensureBootstrap()
        val entity = usuarioDao.getByUsername(username) ?: return LoginResultado.CredencialesInvalidas
        val passwordHash = entity.passwordHash
        if (!entity.activo || passwordHash == null || !passwordHasher.verify(password, passwordHash)) {
            return LoginResultado.CredencialesInvalidas
        }
        return LoginResultado.Exitoso(entity.toDomainUsuario(), accessToken = null)
    }

    private suspend fun ensureBootstrap() {
        rolDao.insertIfEmpty { rolesDeSistema(System.currentTimeMillis()) }
        usuarioDao.insertIfEmpty {
            val rolAdministrador = rolDao.getByNombre(NOMBRE_ROL_ADMINISTRADOR)
                ?: error("Rol administrador no encontrado tras el seed de roles de sistema")
            UsuarioEntity(
                localId = UUID.randomUUID().toString(),
                remoteId = null,
                username = ADMIN_BOOTSTRAP_USERNAME,
                nombreCompleto = "Administrador",
                passwordHash = passwordHasher.hash(ADMIN_BOOTSTRAP_PASSWORD),
                rolId = rolAdministrador.localId,
                activo = true,
                updatedAt = System.currentTimeMillis(),
                isSynced = false,
                deletedAt = null,
            )
        }
    }
}

private fun UsuarioEntity.toDomainUsuario() = Usuario(
    id = localId,
    username = username,
    nombreCompleto = nombreCompleto,
    rolId = rolId,
    activo = activo,
)
