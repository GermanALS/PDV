package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.SucursalDto
import com.pdv.pos.data.remote.dto.SucursalListResponseDto
import com.pdv.pos.domain.model.Sucursal
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.test.assertFailsWith

class RemoteSucursalRepositoryTest {

    @Test
    fun `observeSucursales maps the API response to domain Sucursal`() = runTest {
        val api = mockk<SucursalApiService>()
        coEvery { api.getSucursales() } returns SucursalListResponseDto(
            items = listOf(
                SucursalDto(
                    id = "abc",
                    localId = null,
                    nombre = "Sucursal Centro",
                    direccion = "Av. Siempre Viva 123",
                    activa = true,
                    updatedAt = "2026-08-18T12:00:00Z",
                    isSynced = true,
                    deletedAt = null,
                )
            ),
            page = 1,
            pageSize = 20,
            total = 1,
        )
        val repository = RemoteSucursalRepository(api)

        val result = repository.observeSucursales().first()

        assertEquals(
            listOf(Sucursal(id = "abc", nombre = "Sucursal Centro", direccion = "Av. Siempre Viva 123", activa = true)),
            result,
        )
    }

    @Test
    fun `observeSucursales propagates network failures to the collector`() = runTest {
        val api = mockk<SucursalApiService>()
        coEvery { api.getSucursales() } throws IOException("sin conexion")
        val repository = RemoteSucursalRepository(api)

        assertFailsWith<IOException> { repository.observeSucursales().first() }
    }
}
