package com.pdv.pos.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
