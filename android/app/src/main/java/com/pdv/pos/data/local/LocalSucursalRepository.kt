package com.pdv.pos.data.local

import com.pdv.pos.domain.model.Sucursal
import com.pdv.pos.domain.repository.SucursalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val NOMBRE_SUCURSAL_POR_DEFECTO = "Sucursal principal"

// Sucursal por defecto al primer arranque en modo local (PLAN.md Parte 3,
// "Sucursal por defecto"): remoteId nulo hasta que se sincronice.
@Singleton
class LocalSucursalRepository @Inject constructor(
    private val dao: SucursalDao,
) : SucursalRepository {

    override fun observeSucursales(): Flow<List<Sucursal>> = flow {
        ensureDefaultSucursal()
        emitAll(dao.observeAll().map { entities -> entities.map { it.toDomain() } })
    }

    private suspend fun ensureDefaultSucursal() {
        dao.insertIfEmpty {
            SucursalEntity(
                localId = UUID.randomUUID().toString(),
                remoteId = null,
                nombre = NOMBRE_SUCURSAL_POR_DEFECTO,
                direccion = null,
                activa = true,
                updatedAt = System.currentTimeMillis(),
                isSynced = false,
                deletedAt = null,
            )
        }
    }
}

private fun SucursalEntity.toDomain() = Sucursal(
    id = localId,
    nombre = nombre,
    direccion = direccion,
    activa = activa,
)
