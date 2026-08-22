package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.UsuarioCreateRequestDto
import com.pdv.pos.data.remote.dto.UsuarioDto
import com.pdv.pos.data.remote.dto.UsuarioUpdateRequestDto
import com.pdv.pos.domain.model.Usuario
import com.pdv.pos.domain.repository.UsuarioRepository
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteUsuarioRepository @Inject constructor(
    private val api: UsuarioApiService,
    private val appLogger: AppLogger,
) : UsuarioRepository {

    // La lectura propaga fallas de red por el Flow sin capturarlas aca,
    // mismo criterio que RemoteSucursalRepository/RemoteInventarioRepository.
    override fun observeUsuarios(): Flow<List<Usuario>> = flow {
        emit(api.getUsuarios().items.map { it.toDomain() })
    }

    override suspend fun crearUsuario(usuario: Usuario, passwordHash: String?, sucursalId: String, actorUsuario: String) {
        try {
            api.createUsuario(usuario.toCreateRequestDto(passwordHash))
        } catch (e: IOException) {
            logFallo("crear", usuario.username, sucursalId, actorUsuario, e)
            throw e
        } catch (e: HttpException) {
            logFallo("crear", usuario.username, sucursalId, actorUsuario, e)
            throw e
        }
    }

    override suspend fun actualizarUsuario(usuario: Usuario, passwordHash: String?, sucursalId: String, actorUsuario: String) {
        try {
            api.updateUsuario(usuario.id, usuario.toUpdateRequestDto(passwordHash))
        } catch (e: IOException) {
            logFallo("actualizar", usuario.username, sucursalId, actorUsuario, e)
            throw e
        } catch (e: HttpException) {
            logFallo("actualizar", usuario.username, sucursalId, actorUsuario, e)
            throw e
        }
    }

    override suspend fun eliminarUsuario(usuarioId: String, sucursalId: String, actorUsuario: String) {
        try {
            api.deleteUsuario(usuarioId)
        } catch (e: IOException) {
            logFallo("eliminar", usuarioId, sucursalId, actorUsuario, e)
            throw e
        } catch (e: HttpException) {
            logFallo("eliminar", usuarioId, sucursalId, actorUsuario, e)
            throw e
        }
    }

    private suspend fun logFallo(accion: String, referencia: String, sucursalId: String, actorUsuario: String, e: Exception) {
        appLogger.log(
            LogType.ERROR,
            sucursalId = sucursalId,
            usuario = actorUsuario,
            mensaje = "Fallo de red al $accion usuario: $referencia, ${e.message}",
        )
    }
}

private fun UsuarioDto.toDomain() = Usuario(
    id = id,
    username = username,
    nombreCompleto = nombreCompleto,
    rolId = rolId,
    activo = activo,
)

private fun Usuario.toCreateRequestDto(passwordHash: String?) = UsuarioCreateRequestDto(
    localId = id,
    username = username,
    nombreCompleto = nombreCompleto,
    rolId = rolId,
    activo = activo,
    passwordHash = passwordHash,
)

private fun Usuario.toUpdateRequestDto(passwordHash: String?) = UsuarioUpdateRequestDto(
    username = username,
    nombreCompleto = nombreCompleto,
    rolId = rolId,
    activo = activo,
    passwordHash = passwordHash,
)
