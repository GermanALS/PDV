package com.pdv.pos.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// v9 -> v10 (PLAN.md Parte 36, A-6): columna syncRechazo en las tablas que se
// suben. Las filas que el push descarto antes de esta version quedaron con
// isSynced = 1 sin remoteId: vuelven a no sincronizadas, se marcan rechazadas
// con codigo 0 (desconocido) y se registran en sync_conflicts. Solo las tablas
// donde el push siempre asigna remoteId: los movimientos de venta/devolucion
// se marcan sincronizados sin remoteId de forma legitima y quedan fuera.
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        TABLAS_CON_RECHAZO.forEach { db.execSQL("ALTER TABLE $it ADD COLUMN syncRechazo INTEGER") }
        RECHAZOS_HEREDADOS.forEach { (entidad, tabla, filtro) ->
            val descartadas = "isSynced = 1 AND remoteId IS NULL AND deletedAt IS NULL$filtro"
            db.execSQL(
                "INSERT INTO sync_conflicts (id, entidad, entidadLocalId, sucursalId, valorLocal, valorRemoto, " +
                    "valorResuelto, politicaAplicada, resueltoAutomaticamente, fechaDeteccion) " +
                    "SELECT $UUID_V4_SQL, '$entidad', localId, sucursalId, '$entidad ' || localId, " +
                    "'HTTP desconocido (descartada antes de la Parte 36)', 'rechazada, sin subir', " +
                    "'rechazo_backend', 0, CAST(strftime('%s', 'now') AS INTEGER) * 1000 " +
                    "FROM $tabla WHERE $descartadas",
            )
            db.execSQL("UPDATE $tabla SET isSynced = 0, syncRechazo = 0 WHERE $descartadas")
        }
    }
}

private val TABLAS_CON_RECHAZO = listOf("ventas", "devoluciones", "cortes_caja", "retiros_efectivo", "movimientos", "inventario")

private val RECHAZOS_HEREDADOS = listOf(
    Triple("venta", "ventas", ""),
    Triple("devolucion", "devoluciones", ""),
    Triple("corte_caja", "cortes_caja", ""),
    Triple("retiro_efectivo", "retiros_efectivo", ""),
    Triple("movimiento", "movimientos", " AND tipo IN ('entrada', 'ajuste')"),
)

// UUID v4 en SQLite (sin funcion nativa): mismo formato que UUID.randomUUID().
private const val UUID_V4_SQL =
    "lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || " +
        "substr(lower(hex(randomblob(2))), 2) || '-' || substr('89ab', abs(random()) % 4 + 1, 1) || " +
        "substr(lower(hex(randomblob(2))), 2) || '-' || lower(hex(randomblob(6)))"

// v8 -> v9 (PLAN.md Parte 35): senal edicionPendiente en inventario. Rescata
// las ediciones de catalogo/ubicacion que la Parte 34 dejo sin subir: un
// articulo con remoteId y isSynced = 0 solo puede estar editado localmente
// (el pull nunca crea articulos y solo la entrada limpia el flag). El LWW del
// servidor decide si esas ediciones ganan.
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE inventario ADD COLUMN edicionPendiente INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "UPDATE inventario SET edicionPendiente = 1 WHERE articuloId IN " +
                "(SELECT localId FROM articulos WHERE isSynced = 0 AND remoteId IS NOT NULL)",
        )
    }
}

// v7 -> v8 (M-9, PLAN.md Parte 28): columna espejo cantidadNum (REAL) en
// inventario para poder agregar/ordenar cantidades en SQL. Se rellena
// convirtiendo el TEXT existente; SQLite exige un DEFAULT al agregar una
// columna NOT NULL sobre una tabla con filas.
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE inventario ADD COLUMN cantidadNum REAL NOT NULL DEFAULT 0")
        db.execSQL("UPDATE inventario SET cantidadNum = CAST(cantidad AS REAL)")
    }
}
