package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.RolCreateRequestDto
import com.pdv.pos.data.remote.dto.RolDto
import com.pdv.pos.data.remote.dto.RolUpdateRequestDto
import com.pdv.pos.domain.model.Rol
import com.pdv.pos.domain.repository.RolRepository
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteRolRepository @Inject constructor(
    private val api: RolApiService,
    private val appLogger: AppLogger,
) : RolRepository {

    // La lectura propaga fallas de red por el Flow sin capturarlas aca,
    // mismo criterio que RemoteUsuarioRepository.
    override fun observeRoles(): Flow<List<Rol>> = flow {
        emit(api.getRoles().items.map { it.toDomain() })
    }

    override suspend fun crearRol(rol: Rol, sucursalId: String, actorUsuario: String) {
        try {
            api.createRol(rol.toCreateRequestDto())
        } catch (e: IOException) {
            logFallo("crear", rol.nombre, sucursalId, actorUsuario, e)
            throw e
        } catch (e: HttpException) {
            logFallo("crear", rol.nombre, sucursalId, actorUsuario, e)
            throw e
        }
    }

    override suspend fun actualizarRol(rol: Rol, sucursalId: String, actorUsuario: String) {
        try {
            api.updateRol(rol.id, rol.toUpdateRequestDto())
        } catch (e: IOException) {
            logFallo("actualizar", rol.nombre, sucursalId, actorUsuario, e)
            throw e
        } catch (e: HttpException) {
            logFallo("actualizar", rol.nombre, sucursalId, actorUsuario, e)
            throw e
        }
    }

    override suspend fun eliminarRol(rolId: String, sucursalId: String, actorUsuario: String) {
        try {
            api.deleteRol(rolId)
        } catch (e: IOException) {
            logFallo("eliminar", rolId, sucursalId, actorUsuario, e)
            throw e
        } catch (e: HttpException) {
            logFallo("eliminar", rolId, sucursalId, actorUsuario, e)
            throw e
        }
    }

    private suspend fun logFallo(accion: String, referencia: String, sucursalId: String, actorUsuario: String, e: Exception) {
        appLogger.log(
            LogType.ERROR,
            sucursalId = sucursalId,
            usuario = actorUsuario,
            mensaje = "Fallo de red al $accion rol: $referencia, ${e.message}",
        )
    }
}

private fun RolDto.toDomain() = Rol(
    id = id,
    nombre = nombre,
    modulosPermitidos = modulosPermitidos,
    esSistema = esSistema,
)

private fun Rol.toCreateRequestDto() = RolCreateRequestDto(
    localId = id,
    nombre = nombre,
    modulosPermitidos = modulosPermitidos,
)

private fun Rol.toUpdateRequestDto() = RolUpdateRequestDto(
    nombre = nombre,
    modulosPermitidos = modulosPermitidos,
)
