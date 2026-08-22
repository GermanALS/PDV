package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.AuthLoginRequestDto
import com.pdv.pos.data.remote.dto.UsuarioDto
import com.pdv.pos.domain.model.Usuario
import com.pdv.pos.domain.repository.AuthRepository
import com.pdv.pos.domain.repository.LoginResultado
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteAuthRepository @Inject constructor(
    private val api: AuthApiService,
) : AuthRepository {

    // 401 es un resultado de negocio esperado (credenciales incorrectas),
    // no una falla de red - se traduce a CredencialesInvalidas en vez de
    // propagarse. Cualquier otro HttpException/IOException (timeout, 5xx,
    // sin conexion) se propaga sin capturar, mismo criterio que el resto de
    // los RemoteXRepository del proyecto.
    override suspend fun login(username: String, password: String): LoginResultado {
        val response = try {
            api.login(AuthLoginRequestDto(username = username, password = password))
        } catch (e: HttpException) {
            if (e.code() == 401) return LoginResultado.CredencialesInvalidas
            throw e
        }
        return LoginResultado.Exitoso(response.usuario.toDomain(), accessToken = response.accessToken)
    }
}

private fun UsuarioDto.toDomain() = Usuario(
    id = id,
    username = username,
    nombreCompleto = nombreCompleto,
    rolId = rolId,
    activo = activo,
)
