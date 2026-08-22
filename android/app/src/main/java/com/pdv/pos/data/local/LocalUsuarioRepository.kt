package com.pdv.pos.data.local

import com.pdv.pos.domain.model.Usuario
import com.pdv.pos.domain.repository.UsuarioRepository
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalUsuarioRepository @Inject constructor(
    private val dao: UsuarioDao,
    private val appLogger: AppLogger,
) : UsuarioRepository {

    override fun observeUsuarios(): Flow<List<Usuario>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun crearUsuario(usuario: Usuario, passwordHash: String?, sucursalId: String, actorUsuario: String) {
        dao.insert(usuario.toEntity(now = System.currentTimeMillis(), passwordHash = passwordHash))
        appLogger.log(
            LogType.DB_WRITE,
            sucursalId = sucursalId,
            usuario = actorUsuario,
            mensaje = "Usuario creado: username=${usuario.username}, rolId=${usuario.rolId}",
        )
    }

    override suspend fun actualizarUsuario(usuario: Usuario, passwordHash: String?, sucursalId: String, actorUsuario: String) {
        val existente = dao.getUsuario(usuario.id) ?: error("Usuario no encontrado: ${usuario.id}")
        dao.update(
            existente.copy(
                username = usuario.username,
                nombreCompleto = usuario.nombreCompleto,
                passwordHash = passwordHash ?: existente.passwordHash,
                rolId = usuario.rolId,
                activo = usuario.activo,
                updatedAt = System.currentTimeMillis(),
                isSynced = false,
            ),
        )
        appLogger.log(
            LogType.DB_WRITE,
            sucursalId = sucursalId,
            usuario = actorUsuario,
            mensaje = "Usuario actualizado: username=${usuario.username}, rolId=${usuario.rolId}",
        )
    }

    override suspend fun eliminarUsuario(usuarioId: String, sucursalId: String, actorUsuario: String) {
        val existente = dao.getUsuario(usuarioId) ?: error("Usuario no encontrado: $usuarioId")
        val now = System.currentTimeMillis()
        dao.update(existente.copy(deletedAt = now, updatedAt = now, isSynced = false))
        appLogger.log(
            LogType.DB_WRITE,
            sucursalId = sucursalId,
            usuario = actorUsuario,
            mensaje = "Usuario eliminado: username=${existente.username}",
        )
    }
}

private fun UsuarioEntity.toDomain() = Usuario(
    id = localId,
    username = username,
    nombreCompleto = nombreCompleto,
    rolId = rolId,
    activo = activo,
)

private fun Usuario.toEntity(now: Long, passwordHash: String?) = UsuarioEntity(
    localId = id,
    remoteId = null,
    username = username,
    nombreCompleto = nombreCompleto,
    passwordHash = passwordHash,
    rolId = rolId,
    activo = activo,
    updatedAt = now,
    isSynced = false,
    deletedAt = null,
)
