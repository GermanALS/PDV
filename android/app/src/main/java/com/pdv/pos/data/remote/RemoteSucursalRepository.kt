package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.SucursalDto
import com.pdv.pos.domain.model.Sucursal
import com.pdv.pos.domain.repository.SucursalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

// Las fallas de red se propagan por el Flow (sin capturarlas aca), igual
// que el resto del proyecto las mapea a ApiResult recien en el ViewModel
// que consume el repositorio (ver HelloViewModel.fetchHealth, Parte 2).
@Singleton
class RemoteSucursalRepository @Inject constructor(
    private val api: SucursalApiService,
) : SucursalRepository {

    override fun observeSucursales(): Flow<List<Sucursal>> = flow {
        emit(api.getSucursales().items.map { it.toDomain() })
    }
}

private fun SucursalDto.toDomain() = Sucursal(
    id = id,
    nombre = nombre,
    direccion = direccion,
    activa = activa,
)
