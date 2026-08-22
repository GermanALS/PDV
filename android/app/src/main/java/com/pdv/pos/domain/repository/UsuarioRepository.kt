package com.pdv.pos.domain.repository

import com.pdv.pos.domain.model.Usuario
import kotlinx.coroutines.flow.Flow

// sucursalId/actorUsuario van aparte del Usuario administrado porque
// `usuarios` no es sucursal_scoped (docs/schema-pos.json) - se usan solo
// para atribuir el log DB_WRITE (PLAN.md Parte 5), igual que el resto de
// los repositorios de escritura del proyecto. passwordHash va aparte del
// Usuario por el mismo motivo que nunca esta en el modelo de dominio de
// lectura (PLAN.md Parte 13): null en crearUsuario deja al usuario sin
// contrasena asignada; null en actualizarUsuario no cambia la existente.
interface UsuarioRepository {
    fun observeUsuarios(): Flow<List<Usuario>>
    suspend fun crearUsuario(usuario: Usuario, passwordHash: String?, sucursalId: String, actorUsuario: String)
    suspend fun actualizarUsuario(usuario: Usuario, passwordHash: String?, sucursalId: String, actorUsuario: String)
    suspend fun eliminarUsuario(usuarioId: String, sucursalId: String, actorUsuario: String)
}
