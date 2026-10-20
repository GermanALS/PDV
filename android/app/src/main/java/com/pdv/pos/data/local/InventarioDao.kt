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

    @Insert
    suspend fun insertArticulo(entity: ArticuloEntity)

    // Pull (PLAN.md Parte 35): un articulo creado offline y aun sin subir
    // (remoteId nulo) con el mismo SKU que uno remoto se enlaza en vez de
    // duplicarse.
    @Query("SELECT * FROM articulos WHERE sku = :sku AND remoteId IS NULL LIMIT 1")
    suspend fun getArticuloSinRemotoPorSku(sku: String): ArticuloEntity?

    // Catalogo global: basta una fila de inventario con edicion pendiente, en
    // cualquier sucursal, para que el pull no pise los atributos del articulo.
    @Query("SELECT EXISTS(SELECT 1 FROM inventario WHERE articuloId = :articuloId AND edicionPendiente = 1)")
    suspend fun hayEdicionPendiente(articuloId: String): Boolean

    @Query("SELECT * FROM inventario WHERE sucursalId = :sucursalId AND articuloId = :articuloId LIMIT 1")
    suspend fun getInventario(sucursalId: String, articuloId: String): InventarioEntity?

    // Push diferido (PLAN.md Parte 32, Grupo 2, sub-paso 7b): ajustes
    // manuales creados offline. La unidad a subir es el movimiento tipo
    // "ajuste"; cada uno se envia como delta a POST /ajustes-inventario
    // (Parte 34). Un articulo sin remoteId todavia (entrada aun sin
    // sincronizar) se salta este ciclo.
    @Query("SELECT * FROM movimientos WHERE tipo = 'ajuste' AND isSynced = 0 AND syncRechazo IS NULL AND deletedAt IS NULL")
    suspend fun getMovimientosAjustePendientes(): List<MovimientoEntity>

    @Query("SELECT COUNT(*) FROM movimientos WHERE tipo = 'ajuste' AND isSynced = 0 AND syncRechazo IS NULL AND deletedAt IS NULL")
    suspend fun contarAjustesPendientes(): Int

    // Pull de inventario (PLAN.md Parte 34, A-1(b)): la fila local solo "gana"
    // si quedan movimientos de ese (sucursal, articulo) sin subir, de
    // cualquier tipo. inventario.isSynced no sirve de senal: toda escritura
    // local lo deja en false y nada lo limpia al subir ventas/entradas. La
    // reconciliacion inicial ademas suma su efecto sobre el valor remoto. Se
    // excluyen los movimientos de ventas ya marcadas como sincronizadas o
    // descartadas: antes de la Parte 34 el descarte de una venta dejaba sus
    // movimientos en isSynced = 0 para siempre.
    @Query(
        "SELECT * FROM movimientos WHERE sucursalId = :sucursalId AND articuloId = :articuloId " +
            "AND isSynced = 0 AND syncRechazo IS NULL AND deletedAt IS NULL " +
            "AND NOT (referenciaTipo = 'venta' AND referenciaId IN " +
            "(SELECT localId FROM ventas WHERE isSynced = 1 OR syncRechazo IS NOT NULL))",
    )
    suspend fun getMovimientosPendientes(sucursalId: String, articuloId: String): List<MovimientoEntity>

    // Push de ediciones de catalogo/ubicacion (PLAN.md Parte 35): la unidad a
    // subir es la fila de inventario con edicionPendiente.
    @Query("SELECT * FROM inventario WHERE edicionPendiente = 1 AND deletedAt IS NULL")
    suspend fun getEdicionesPendientes(): List<InventarioEntity>

    @Query("SELECT COUNT(*) FROM inventario WHERE edicionPendiente = 1 AND deletedAt IS NULL")
    suspend fun contarEdicionesPendientes(): Int

    @Query("SELECT * FROM inventario WHERE localId = :localId LIMIT 1")
    suspend fun getInventarioPorLocalId(localId: String): InventarioEntity?

    @Query("UPDATE inventario SET edicionPendiente = 0 WHERE localId = :localId")
    suspend fun limpiarEdicionPendiente(localId: String)

    // Cierra una edicion ya subida. Si el usuario volvio a editar el articulo
    // mientras el PATCH estaba en vuelo (articulos.updatedAt cambio respecto de
    // editadoEnLeido, el valor enviado como editado_en), la edicion nueva sigue
    // pendiente y no se toca nada. `vigente` != null cuando la edicion perdio:
    // se adoptan sus atributos y la ubicacion remota sobre las filas actuales,
    // no sobre copias leidas antes de la llamada de red (una venta o un pull
    // pudieron cambiar cantidad entretanto).
    @Transaction
    suspend fun cerrarEdicionSubida(
        inventarioLocalId: String,
        articuloLocalId: String,
        editadoEnLeido: Long,
        vigente: ArticuloEntity?,
        ubicacionVigente: String?,
    ) {
        val articulo = getArticulo(articuloLocalId) ?: return
        if (articulo.updatedAt != editadoEnLeido) return
        if (vigente != null) {
            updateArticulo(
                articulo.copy(
                    nombre = vigente.nombre,
                    descripcion = vigente.descripcion,
                    categoria = vigente.categoria,
                    unidadMedida = vigente.unidadMedida,
                    precioVenta = vigente.precioVenta,
                    costo = vigente.costo,
                    updatedAt = vigente.updatedAt,
                ),
            )
            getInventarioPorLocalId(inventarioLocalId)?.let { updateInventario(it.copy(ubicacion = ubicacionVigente)) }
        }
        limpiarEdicionPendiente(inventarioLocalId)
    }

    @Query("UPDATE movimientos SET remoteId = :remoteId, isSynced = 1 WHERE localId = :localId")
    suspend fun marcarMovimientoSincronizado(localId: String, remoteId: String)

    // Rechazo del backend (ver VentaDao.marcarVentaRechazada).
    @Query("UPDATE movimientos SET syncRechazo = :codigo WHERE localId = :localId")
    suspend fun marcarMovimientoRechazado(localId: String, codigo: Int)

    @Query("SELECT COUNT(*) FROM movimientos WHERE tipo = 'ajuste' AND syncRechazo IS NOT NULL AND deletedAt IS NULL")
    suspend fun contarAjustesRechazados(): Int

    // Ultimo resultado del push de la edicion de catalogo de esta fila: el
    // codigo del rechazo, o null si se subio (PLAN.md Parte 36).
    @Query("UPDATE inventario SET syncRechazo = :codigo WHERE localId = :localId")
    suspend fun marcarRechazoEdicion(localId: String, codigo: Int?)

    @Query("SELECT COUNT(*) FROM inventario WHERE syncRechazo IS NOT NULL AND deletedAt IS NULL")
    suspend fun contarEdicionesRechazadas(): Int

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
    // 0) no se inserta movimiento. Si cambio el catalogo o la ubicacion se
    // marca edicionPendiente en la fila de inventario (PLAN.md Parte 35); un
    // guardado que solo cambia stock no marca nada ni toca el articulo.
    @Transaction
    suspend fun actualizarArticuloCompleto(
        articulo: ArticuloEntity,
        sucursalId: String,
        usuarioId: String,
        nuevaCantidad: BigDecimal,
        ubicacion: String?,
        now: Long,
    ) {
        val previo = getArticulo(articulo.localId)
        val existente = getInventario(sucursalId, articulo.localId)
        val huboEdicion = previo == null || previo.catalogoDifiere(articulo) || ubicacion != existente?.ubicacion
        if (huboEdicion) updateArticulo(articulo)

        val cantidadConocida = existente?.cantidad ?: BigDecimal.ZERO
        val delta = nuevaCantidad - cantidadConocida
        if (delta.compareTo(BigDecimal.ZERO) == 0 && existente != null) {
            if (huboEdicion) {
                updateInventario(
                    existente.copy(ubicacion = ubicacion, updatedAt = now, isSynced = false, edicionPendiente = true),
                )
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
                    edicionPendiente = huboEdicion,
                ),
            )
        } else {
            updateInventario(
                existente
                    .conCantidad(EventoAditivoCombiner.combinar(existente.cantidad, delta, BigDecimal.ZERO), now)
                    .copy(ubicacion = ubicacion, edicionPendiente = existente.edicionPendiente || huboEdicion),
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
