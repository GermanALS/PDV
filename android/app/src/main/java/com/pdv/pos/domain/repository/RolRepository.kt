package com.pdv.pos.domain.repository

import com.pdv.pos.domain.model.Rol
import kotlinx.coroutines.flow.Flow

// sucursalId/actorUsuario van aparte del Rol administrado por el mismo
// motivo que UsuarioRepository: `roles` no es sucursal_scoped, se usan solo
// para atribuir el log DB_WRITE (PLAN.md Parte 5).
interface RolRepository {
    fun observeRoles(): Flow<List<Rol>>
    suspend fun crearRol(rol: Rol, sucursalId: String, actorUsuario: String)
    suspend fun actualizarRol(rol: Rol, sucursalId: String, actorUsuario: String)
    suspend fun eliminarRol(rolId: String, sucursalId: String, actorUsuario: String)
}
