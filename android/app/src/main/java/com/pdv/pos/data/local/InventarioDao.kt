package com.pdv.pos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.pdv.pos.sync.EventoAditivoCombiner
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.util.UUID

private const val TIPO_MOVIMIENTO_AJUSTE = "ajuste"
private const val REFERENCIA_AJUSTE_MANUAL = "ajuste_manual"

@Dao
interface InventarioDao {

    @Query(
        """
        SELECT a.localId AS articuloLocalId, a.codigoBarras AS codigoBarras, a.sku AS sku, a.nombre AS nombre,
               a.descripcion AS descripcion, a.categoria AS categoria, a.unidadMedida AS unidadMedida,
               a.precioVenta AS precioVenta, a.costo AS costo, a.activo AS activo,
               i.cantidad AS cantidad, i.ubicacion AS ubicacion
        FROM inventario i
        INNER JOIN articulos a ON a.localId = i.articuloId
        WHERE i.sucursalId = :sucursalId AND i.deletedAt IS NULL AND a.deletedAt IS NULL
          AND (:termino = '' OR a.nombre LIKE '%' || :termino || '%' OR a.sku LIKE '%' || :termino || '%'
               OR a.codigoBarras LIKE '%' || :termino || '%')
        ORDER BY a.nombre
        LIMIT :limite OFFSET :desplazamiento
        """,
    )
    fun observarPagina(
        sucursalId: String,
        termino: String,
        limite: Int,
        desplazamiento: Int,
    ): Flow<List<InventarioConArticuloRow>>

    @Query(
        """
        SELECT COUNT(*) FROM inventario i
        INNER JOIN articulos a ON a.localId = i.articuloId
        WHERE i.sucursalId = :sucursalId AND i.deletedAt IS NULL AND a.deletedAt IS NULL
          AND (:termino = '' OR a.nombre LIKE '%' || :termino || '%' OR a.sku LIKE '%' || :termino || '%'
               OR a.codigoBarras LIKE '%' || :termino || '%')
        """,
    )
    fun observarTotal(sucursalId: String, termino: String): Flow<Int>

    // Suma de existencias en SQL sobre cantidadNum (M-9): un escalar, no
    // depende del tamano del catalogo. termino = '' desactiva el LIKE;
    // categoria = null desactiva el filtro por categoria.
    @Query(
        """
        SELECT COALESCE(SUM(i.cantidadNum), 0.0) FROM inventario i
        INNER JOIN articulos a ON a.localId = i.articuloId
        WHERE i.sucursalId = :sucursalId AND i.deletedAt IS NULL AND a.deletedAt IS NULL
          AND (:termino = '' OR a.nombre LIKE '%' || :termino || '%' OR a.sku LIKE '%' || :termino || '%'
               OR a.codigoBarras LIKE '%' || :termino || '%')
          AND (:categoria IS NULL OR a.categoria = :categoria COLLATE NOCASE)
        """,
    )
    suspend fun sumarCantidad(sucursalId: String, termino: String, categoria: String?): Double

    // Misma pagina que observarPagina pero ordenada por cantidad numerica
    // (cantidadNum), no por nombre - la usa el camino de exportacion (M-9)
    // para que el CSV/Excel salga por existencia ascendente y el orden sea
    // fiable (el ORDER BY sobre el TEXT de cantidad seria lexicografico).
    @Query(
        """
        SELECT a.localId AS articuloLocalId, a.codigoBarras AS codigoBarras, a.sku AS sku, a.nombre AS nombre,
               a.descripcion AS descripcion, a.categoria AS categoria, a.unidadMedida AS unidadMedida,
               a.precioVenta AS precioVenta, a.costo AS costo, a.activo AS activo,
               i.cantidad AS cantidad, i.ubicacion AS ubicacion
        FROM inventario i
        INNER JOIN articulos a ON a.localId = i.articuloId
        WHERE i.sucursalId = :sucursalId AND i.deletedAt IS NULL AND a.deletedAt IS NULL
          AND (:termino = '' OR a.nombre LIKE '%' || :termino || '%' OR a.sku LIKE '%' || :termino || '%'
               OR a.codigoBarras LIKE '%' || :termino || '%')
        ORDER BY i.cantidadNum ASC, a.nombre
        LIMIT :limite OFFSET :desplazamiento
        """,
    )
    fun observarPaginaExport(
        sucursalId: String,
        termino: String,
        limite: Int,
        desplazamiento: Int,
    ): Flow<List<InventarioConArticuloRow>>

    @Query("SELECT DISTINCT categoria FROM articulos WHERE categoria IS NOT NULL AND deletedAt IS NULL ORDER BY categoria")
    fun observarCategorias(): Flow<List<String>>

    @Query("SELECT DISTINCT unidadMedida FROM articulos WHERE deletedAt IS NULL ORDER BY unidadMedida")
    fun observarUnidadesMedida(): Flow<List<String>>

    @Query(
        "SELECT DISTINCT ubicacion FROM inventario WHERE sucursalId = :sucursalId AND ubicacion IS NOT NULL AND deletedAt IS NULL ORDER BY ubicacion",
    )
    fun observarUbicaciones(sucursalId: String): Flow<List<String>>

    @Query("SELECT * FROM articulos WHERE localId = :articuloId LIMIT 1")
    suspend fun getArticulo(articuloId: String): ArticuloEntity?

    // Pull diferido (PLAN.md Parte 32, Grupo 3): el GET /inventario devuelve
    // el articulo_id del backend; se correlaciona con el articulo local por
    // remoteId para ubicar/actualizar su fila de inventario.
    @Query("SELECT * FROM articulos WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getArticuloByRemoteId(remoteId: String): ArticuloEntity?

    @Update
    suspend fun updateArticulo(entity: ArticuloEntity)

    @Query("SELECT * FROM inventario WHERE sucursalId = :sucursalId AND articuloId = :articuloId LIMIT 1")
    suspend fun getInventario(sucursalId: String, articuloId: String): InventarioEntity?

    // Push diferido (PLAN.md Parte 32, Grupo 2, sub-paso 7b): ajustes
    // manuales creados offline. La unidad a subir es el movimiento tipo
    // "ajuste"; se agrupan por (sucursalId, articuloId) y se envia un solo
    // PATCH /inventario/{articulo} con la cantidad actual (el backend calcula
    // su propio delta). Un articulo sin remoteId todavia (entrada aun sin
    // sincronizar) se salta este ciclo.
    @Query("SELECT * FROM movimientos WHERE tipo = 'ajuste' AND isSynced = 0 AND deletedAt IS NULL")
    suspend fun getMovimientosAjustePendientes(): List<MovimientoEntity>

    @Query("SELECT COUNT(*) FROM movimientos WHERE tipo = 'ajuste' AND isSynced = 0 AND deletedAt IS NULL")
    suspend fun contarAjustesPendientes(): Int

    @Query("UPDATE movimientos SET remoteId = :remoteId, isSynced = 1 WHERE localId = :localId")
    suspend fun marcarMovimientoSincronizado(localId: String, remoteId: String)

    @Query("UPDATE movimientos SET isSynced = 1 WHERE localId = :localId")
    suspend fun marcarMovimientoSincronizadoSinRemoto(localId: String)

    @Query("UPDATE articulos SET remoteId = :remoteId, isSynced = 1 WHERE localId = :localId")
    suspend fun marcarArticuloSincronizado(localId: String, remoteId: String)

    @Query("UPDATE inventario SET remoteId = :remoteId, isSynced = 1 WHERE localId = :localId")
    suspend fun marcarInventarioSincronizado(localId: String, remoteId: String)

    @Insert
    suspend fun insertInventario(entity: InventarioEntity)

    @Update
    suspend fun updateInventario(entity: InventarioEntity)

    @Insert
    suspend fun insertMovimiento(entity: MovimientoEntity)

    // Atributos de catalogo (UPDATE directo, last-write-wins) + ajuste de
    // cantidad en existencia (delta con signo via EventoAditivoCombiner,
    // nunca UPDATE cantidad = X directo) + movimiento tipo "ajuste", todo en
    // una sola transaccion Room (PLAN.md Parte 9, mismo patron que
    // EntradaDao.insertEntradaCompleta). Si la cantidad no cambio (delta =
    // 0) no se toca inventario ni se inserta movimiento - solo el articulo.
    @Transaction
    suspend fun actualizarArticuloCompleto(
        articulo: ArticuloEntity,
        sucursalId: String,
        usuarioId: String,
        nuevaCantidad: BigDecimal,
        ubicacion: String?,
        now: Long,
    ) {
        updateArticulo(articulo)

        val existente = getInventario(sucursalId, articulo.localId)
        val cantidadConocida = existente?.cantidad ?: BigDecimal.ZERO
        val delta = nuevaCantidad - cantidadConocida
        if (delta.compareTo(BigDecimal.ZERO) == 0 && existente != null) {
            if (ubicacion != existente.ubicacion) {
                updateInventario(existente.copy(ubicacion = ubicacion, updatedAt = now, isSynced = false))
            }
            return
        }

        if (existente == null) {
            insertInventario(
                nuevoInventario(
                    sucursalId = sucursalId,
                    articuloId = articulo.localId,
                    cantidad = EventoAditivoCombiner.combinar(BigDecimal.ZERO, delta, BigDecimal.ZERO),
                    ubicacion = ubicacion,
                    now = now,
                ),
            )
        } else {
            updateInventario(
                existente
                    .conCantidad(EventoAditivoCombiner.combinar(existente.cantidad, delta, BigDecimal.ZERO), now)
                    .copy(ubicacion = ubicacion),
            )
        }

        insertMovimiento(
            MovimientoEntity(
                localId = UUID.randomUUID().toString(),
                remoteId = null,
                sucursalId = sucursalId,
                articuloId = articulo.localId,
                usuarioId = usuarioId,
                tipo = TIPO_MOVIMIENTO_AJUSTE,
                cantidad = delta,
                ubicacion = ubicacion,
                referenciaTipo = REFERENCIA_AJUSTE_MANUAL,
                referenciaId = null,
                fecha = now,
                updatedAt = now,
                isSynced = false,
                deletedAt = null,
            ),
        )
    }
}
