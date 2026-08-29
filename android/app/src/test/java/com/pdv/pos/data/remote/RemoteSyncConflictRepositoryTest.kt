package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.SyncConflictCreateRequestDto
import com.pdv.pos.data.remote.dto.SyncConflictDto
import com.pdv.pos.data.remote.dto.SyncConflictListResponseDto
import com.pdv.pos.domain.model.SyncConflict
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Instant
import kotlin.test.assertFailsWith

class RemoteSyncConflictRepositoryTest {

    private fun dto(id: String) = SyncConflictDto(
        id = id,
        entidad = "inventario",
        entidadLocalId = "local-$id",
        sucursalId = "suc-1",
        valorLocal = Json.parseToJsonElement("""{"cantidad":2}"""),
        valorRemoto = Json.parseToJsonElement("""{"cantidad":3}"""),
        valorResuelto = Json.parseToJsonElement("""{"cantidad":-1}"""),
        politicaAplicada = "evento_aditivo",
        resueltoAutomaticamente = false,
        fechaDeteccion = "2026-08-28T14:03:11Z",
    )

    @Test
    fun `observeConflictos maps the API response to domain SyncConflict`() = runTest {
        val api = mockk<SyncConflictApiService>()
        coEvery { api.getSyncConflicts(any(), any()) } returns SyncConflictListResponseDto(
            items = listOf(dto("c1")),
            page = 1,
            pageSize = 100,
            total = 1,
        )
        val repository = RemoteSyncConflictRepository(api)

        val result = repository.observeConflictos().first().single()

        assertEquals(
            SyncConflict(
                id = "c1",
                entidad = "inventario",
                entidadLocalId = "local-c1",
                sucursalId = "suc-1",
                valorLocal = """{"cantidad":2}""",
                valorRemoto = """{"cantidad":3}""",
                valorResuelto = """{"cantidad":-1}""",
                politicaAplicada = "evento_aditivo",
                resueltoAutomaticamente = false,
                fechaDeteccion = Instant.parse("2026-08-28T14:03:11Z").toEpochMilli(),
            ),
            result,
        )
    }

    @Test
    fun `observeConflictos propagates network failures to the collector`() = runTest {
        val api = mockk<SyncConflictApiService>()
        coEvery { api.getSyncConflicts(any(), any()) } throws IOException("sin conexion")
        val repository = RemoteSyncConflictRepository(api)

        assertFailsWith<IOException> { repository.observeConflictos().first() }
    }

    @Test
    fun `subirConflicto maps the domain model to the create request`() = runTest {
        val api = mockk<SyncConflictApiService>()
        val requestSlot = slot<SyncConflictCreateRequestDto>()
        coEvery { api.createSyncConflict(capture(requestSlot)) } returns dto("c9")
        val repository = RemoteSyncConflictRepository(api)

        val conflicto = SyncConflict(
            id = "c9",
            entidad = "articulos",
            entidadLocalId = "local-c9",
            sucursalId = null,
            valorLocal = """{"precio":"18.50"}""",
            valorRemoto = """{"precio":"19.00"}""",
            valorResuelto = """{"precio":"19.00"}""",
            politicaAplicada = "last_write_wins",
            resueltoAutomaticamente = true,
            fechaDeteccion = 1_756_389_791_000L,
        )

        repository.subirConflicto(conflicto)

        val request = requestSlot.captured
        assertEquals("c9", request.id)
        assertEquals("articulos", request.entidad)
        assertEquals(null, request.sucursalId)
        assertEquals(Json.parseToJsonElement("""{"precio":"19.00"}"""), request.valorResuelto)
        assertEquals("last_write_wins", request.politicaAplicada)
        assertEquals(1_756_389_791_000L, Instant.parse(request.fechaDeteccion).toEpochMilli())
        coVerify(exactly = 1) { api.createSyncConflict(any()) }
    }
}
