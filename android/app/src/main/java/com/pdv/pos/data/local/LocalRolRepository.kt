package com.pdv.pos.data.local

import com.pdv.pos.domain.model.Rol
import com.pdv.pos.domain.repository.RolRepository
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalRolRepository @Inject constructor(
    private val dao: RolDao,
    private val appLogger: AppLogger,
) : RolRepository {

    override fun observeRoles(): Flow<List<Rol>> = flow {
        ensureRolesDeSistema()
        emitAll(dao.observeAll().map { entities -> entities.map { it.toDomain() } })
    }

    private suspend fun ensureRolesDeSistema() {
        dao.insertIfEmpty { rolesDeSistema(System.currentTimeMillis()) }
    }

    override suspend fun crearRol(rol: Rol, sucursalId: String, actorUsuario: String) {
        dao.insert(rol.toEntity(now = System.currentTimeMillis(), esSistema = false))
        appLogger.log(
            LogType.DB_WRITE,
            sucursalId = sucursalId,
            usuario = actorUsuario,
            mensaje = "Rol creado: nombre=${rol.nombre}",
        )
    }

    override suspend fun actualizarRol(rol: Rol, sucursalId: String, actorUsuario: String) {
        val existente = dao.getRol(rol.id) ?: error("Rol no encontrado: ${rol.id}")
        check(!existente.esSistema) { "No se puede modificar un rol de sistema: ${existente.nombre}" }
        dao.update(
            existente.copy(
                nombre = rol.nombre,
                modulosPermitidos = rol.modulosPermitidos,
                updatedAt = System.currentTimeMillis(),
                isSynced = false,
            ),
        )
        appLogger.log(
            LogType.DB_WRITE,
            sucursalId = sucursalId,
            usuario = actorUsuario,
            mensaje = "Rol actualizado: nombre=${rol.nombre}",
        )
    }

    override suspend fun eliminarRol(rolId: String, sucursalId: String, actorUsuario: String) {
        val existente = dao.getRol(rolId) ?: error("Rol no encontrado: $rolId")
        check(!existente.esSistema) { "No se puede eliminar un rol de sistema: ${existente.nombre}" }
        val now = System.currentTimeMillis()
        dao.update(existente.copy(deletedAt = now, updatedAt = now, isSynced = false))
        appLogger.log(
            LogType.DB_WRITE,
            sucursalId = sucursalId,
            usuario = actorUsuario,
            mensaje = "Rol eliminado: nombre=${existente.nombre}",
        )
    }
}

private fun RolEntity.toDomain() = Rol(
    id = localId,
    nombre = nombre,
    modulosPermitidos = modulosPermitidos,
    esSistema = esSistema,
)

private fun Rol.toEntity(now: Long, esSistema: Boolean) = RolEntity(
    localId = id,
    remoteId = null,
    nombre = nombre,
    modulosPermitidos = modulosPermitidos,
    esSistema = esSistema,
    updatedAt = now,
    isSynced = false,
    deletedAt = null,
)
