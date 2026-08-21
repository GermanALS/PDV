package com.pdv.pos.data.local

import com.pdv.pos.domain.model.Devolucion
import com.pdv.pos.domain.model.DevolucionLinea
import com.pdv.pos.domain.repository.DevolucionRepository
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalDevolucionRepository @Inject constructor(
    private val dao: DevolucionDao,
    private val appLogger: AppLogger,
) : DevolucionRepository {

    override suspend fun registrarDevolucion(devolucion: Devolucion) {
        val now = System.currentTimeMillis()
        dao.insertDevolucionCompleta(
            devolucion = devolucion.toEntity(now),
            detalles = devolucion.lineas.map { it.toEntity(devolucionId = devolucion.id, now = now) },
        )

        appLogger.log(
            LogType.DB_WRITE,
            sucursalId = devolucion.sucursalId,
            usuario = devolucion.usuarioId,
            mensaje = "Devolución registrada: folio=${devolucion.folio}, lineas=${devolucion.lineas.size}",
        )
    }
}

private fun Devolucion.toEntity(now: Long) = DevolucionEntity(
    localId = id,
    remoteId = null,
    sucursalId = sucursalId,
    usuarioId = usuarioId,
    ventaId = ventaId,
    folio = folio,
    fecha = fecha,
    estado = estado,
    updatedAt = now,
    isSynced = false,
    deletedAt = null,
)

private fun DevolucionLinea.toEntity(devolucionId: String, now: Long) = DevolucionDetalleEntity(
    localId = UUID.randomUUID().toString(),
    remoteId = null,
    devolucionId = devolucionId,
    articuloId = articuloId,
    cantidad = cantidad,
    motivo = motivo,
    condicion = condicion,
    updatedAt = now,
    isSynced = false,
    deletedAt = null,
)
