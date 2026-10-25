package com.pdv.pos.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room migration tests against the exported schemas in `app/schemas`.
 *
 * Template for future migrations: create the DB at the old version, insert
 * raw rows, then [MigrationTestHelper.runMigrationsAndValidate] checks the
 * resulting schema against the exported JSON of the new version.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PdvDatabase::class.java,
    )

    @Test
    fun migrate7To8_fillsCantidadNumFromTextCantidad() {
        helper.createDatabase(TEST_DB, 7).use { db ->
            insertInventarioV7(db, "inv-1", "9")
            insertInventarioV7(db, "inv-2", "10.5")
            insertInventarioV7(db, "inv-3", "2")
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)

        db.query("SELECT localId FROM inventario ORDER BY cantidadNum").use { cursor ->
            val ordered = buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            assertEquals(listOf("inv-3", "inv-1", "inv-2"), ordered)
        }
        db.query("SELECT SUM(cantidadNum) FROM inventario").use { cursor ->
            cursor.moveToFirst()
            assertEquals(21.5, cursor.getDouble(0), 0.0)
        }
        db.close()
    }

    private fun insertInventarioV7(
        db: SupportSQLiteDatabase,
        localId: String,
        cantidad: String,
    ) {
        db.execSQL(
            "INSERT INTO inventario (localId, remoteId, sucursalId, articuloId, cantidad, " +
                "ubicacion, updatedAt, isSynced, deletedAt) VALUES (?, NULL, 'suc-1', ?, ?, NULL, 0, 0, NULL)",
            arrayOf(localId, "art-$localId", cantidad),
        )
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
