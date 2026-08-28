package com.pdv.pos.data.local

import com.pdv.pos.domain.model.RetiroEfectivo
import com.pdv.pos.domain.repository.RetiroEfectivoRepository
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalRetiroEfectivoRepository @Inject constructor(
    private val dao: RetiroDao,
    private val appLogger: AppLogger,
) : RetiroEfectivoRepository {

    override suspend fun registrarRetiro(retiro: RetiroEfectivo) {
        val now = System.currentTimeMillis()
        dao.insertRetiro(retiro.toEntity(now))

        appLogger.log(
            LogType.DB_WRITE,
            sucursalId = retiro.sucursalId,
            usuario = retiro.usuarioId,
            mensaje = "Retiro de efectivo registrado: monto=${retiro.monto}",
        )
    }

    override fun observeRetiros(sucursalId: String): Flow<List<RetiroEfectivo>> =
        dao.observarRetiros(sucursalId).map { entidades -> entidades.map { it.toDomain() } }
}

private fun RetiroEfectivoEntity.toDomain() = RetiroEfectivo(
    id = localId,
    sucursalId = sucursalId,
    usuarioId = usuarioId,
    monto = monto,
    motivo = motivo,
    fecha = fecha,
)

private fun RetiroEfectivo.toEntity(now: Long) = RetiroEfectivoEntity(
    localId = id,
    remoteId = null,
    sucursalId = sucursalId,
    usuarioId = usuarioId,
    monto = monto,
    motivo = motivo,
    fecha = fecha,
    updatedAt = now,
    isSynced = false,
    deletedAt = null,
)
