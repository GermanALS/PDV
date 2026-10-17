package com.pdv.pos.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
