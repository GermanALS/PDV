package com.pdv.pos.sync

import com.pdv.pos.data.local.SyncConflictDao
import com.pdv.pos.data.local.SyncConflictEntity
import com.pdv.pos.domain.model.Sucursal
import com.pdv.pos.logging.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LastWriteWinsSyncEngineTest {

    private class FakeSyncConflictDao : SyncConflictDao {
        val inserted = mutableListOf<SyncConflictEntity>()
        override suspend fun insert(entity: SyncConflictEntity) {
            inserted.add(entity)
        }
        override suspend fun getAll(): List<SyncConflictEntity> = inserted.toList()
        override fun observeAll(): Flow<List<SyncConflictEntity>> = flowOf(inserted.toList())
    }

    @Test
    fun `no conflict is recorded when both versions already match`(@TempDir tempDir: File) = runTest {
        val dao = FakeSyncConflictDao()
        val engine = LastWriteWinsSyncEngine(dao, AppLogger(tempDir))
        val sucursal = Sucursal(id = "local-1", nombre = "Sucursal Centro")
        val local = Versioned(value = sucursal, updatedAt = 1_000L)
        val remote = Versioned(value = sucursal, updatedAt = 1_000L)

        engine.sincronizar(
            entidad = "sucursales",
            entidadLocalId = "local-1",
            sucursalId = null,
            usuario = "admin",
            local = local,
            remote = remote,
            serializar = { it.toString() },
        )

        assertTrue(dao.inserted.isEmpty())
    }

    @Test
    fun `syncing two versions of the same Sucursal with different updatedAt makes the most recent one win`(
        @TempDir tempDir: File,
    ) = runTest {
        val dao = FakeSyncConflictDao()
        val engine = LastWriteWinsSyncEngine(dao, AppLogger(tempDir))
        val local = Versioned(value = Sucursal(id = "local-1", nombre = "Sucursal Vieja"), updatedAt = 1_000L)
        val remote = Versioned(value = Sucursal(id = "local-1", nombre = "Sucursal Nueva"), updatedAt = 2_000L)

        val resultado = engine.sincronizar(
            entidad = "sucursales",
            entidadLocalId = "local-1",
            sucursalId = null,
            usuario = "admin",
            local = local,
            remote = remote,
            serializar = { it.toString() },
        )

        assertEquals("Sucursal Nueva", resultado.nombre)
    }

    @Test
    fun `a version mismatch is recorded as a conflict in sync_conflicts and in the log`(@TempDir tempDir: File) = runTest {
        val dao = FakeSyncConflictDao()
        val engine = LastWriteWinsSyncEngine(dao, AppLogger(tempDir))
        val local = Versioned(value = Sucursal(id = "local-1", nombre = "Sucursal Vieja"), updatedAt = 1_000L)
        val remote = Versioned(value = Sucursal(id = "local-1", nombre = "Sucursal Nueva"), updatedAt = 2_000L)

        engine.sincronizar(
            entidad = "sucursales",
            entidadLocalId = "local-1",
            sucursalId = null,
            usuario = "admin",
            local = local,
            remote = remote,
            serializar = { it.toString() },
        )

        val conflicto = dao.inserted.single()
        assertEquals("sucursales", conflicto.entidad)
        assertEquals("local-1", conflicto.entidadLocalId)
        assertEquals("last_write_wins", conflicto.politicaAplicada)
        assertTrue(conflicto.resueltoAutomaticamente)
        assertTrue(conflicto.valorLocal.contains("Sucursal Vieja"))
        assertTrue(conflicto.valorRemoto.contains("Sucursal Nueva"))
        assertTrue(conflicto.valorResuelto.contains("Sucursal Nueva"))

        val logContent = tempDir.listFiles()!!.single().readText()
        assertTrue(logContent.contains("[SYNC_CONFLICT]"))
        assertTrue(logContent.contains("sucursales"))
    }
}
