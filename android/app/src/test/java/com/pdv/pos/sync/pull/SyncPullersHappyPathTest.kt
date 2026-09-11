package com.pdv.pos.sync.pull

import com.pdv.pos.data.local.CajaDao
import com.pdv.pos.data.local.CorteCajaEntity
import com.pdv.pos.data.local.RetiroDao
import com.pdv.pos.data.remote.CajaApiService
import com.pdv.pos.data.remote.RetiroApiService
import com.pdv.pos.data.remote.dto.CorteCajaDto
import com.pdv.pos.data.remote.dto.CorteCajaListResponseDto
import com.pdv.pos.data.remote.dto.RetiroEfectivoDto
import com.pdv.pos.data.remote.dto.RetiroEfectivoListResponseDto
import com.pdv.pos.sync.SyncStateStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SyncPullersHappyPathTest {

    private val syncStateStore = mockk<SyncStateStore>(relaxUnitFun = true)

    private fun corteDto(id: String, localId: String?) = CorteCajaDto(
        id = id, localId = localId, sucursalId = "suc-1", usuarioId = "admin", tipo = "final",
        fechaInicio = "2026-08-20T08:00:00Z", fechaFin = "2026-08-20T14:00:00Z", totalVentas = "0",
        totalEfectivo = "0", totalTarjeta = "0", totalRetiros = "0", montoEsperado = "0",
        updatedAt = "2026-08-20T14:00:00Z",
    )

    private fun retiroDto(id: String, localId: String?) = RetiroEfectivoDto(
        id = id, localId = localId, sucursalId = "suc-1", usuarioId = "admin", monto = "10",
        motivo = "gasto", fecha = "2026-08-20T12:00:00Z", updatedAt = "2026-08-20T14:00:00Z",
    )

    @Test
    fun `CorteCajaPuller inserts an unseen corte and advances the cursor`() = runTest {
        val api = mockk<CajaApiService>()
        val dao = mockk<CajaDao>(relaxUnitFun = true)
        coEvery { syncStateStore.pullCursor("cortes_caja") } returns null
        coEvery { api.getCortes(any(), any(), any(), any(), any(), any()) } returns
            CorteCajaListResponseDto(items = listOf(corteDto("remoto-1", localId = "corte-x")), page = 1, pageSize = 100, total = 1)
        coEvery { dao.getCorteByLocalId("corte-x") } returns null

        CorteCajaPuller(api, dao, syncStateStore).pull("suc-1")

        coVerify { dao.insertCorte(match<CorteCajaEntity> { it.localId == "corte-x" && it.remoteId == "remoto-1" && it.isSynced }) }
        coVerify { syncStateStore.setPullCursor("cortes_caja", "2026-08-20T14:00:00Z") }
    }

    @Test
    fun `CorteCajaPuller skips a corte it already has`() = runTest {
        val api = mockk<CajaApiService>()
        val dao = mockk<CajaDao>(relaxUnitFun = true)
        coEvery { syncStateStore.pullCursor("cortes_caja") } returns null
        coEvery { api.getCortes(any(), any(), any(), any(), any(), any()) } returns
            CorteCajaListResponseDto(items = listOf(corteDto("remoto-1", localId = "corte-x")), page = 1, pageSize = 100, total = 1)
        coEvery { dao.getCorteByLocalId("corte-x") } returns mockk()

        CorteCajaPuller(api, dao, syncStateStore).pull("suc-1")

        coVerify(exactly = 0) { dao.insertCorte(any()) }
    }

    @Test
    fun `RetiroPuller inserts an unseen retiro using the id when local_id is absent`() = runTest {
        val api = mockk<RetiroApiService>()
        val dao = mockk<RetiroDao>(relaxUnitFun = true)
        coEvery { syncStateStore.pullCursor("retiros_efectivo") } returns null
        coEvery { api.getRetiros(any(), any(), any(), any(), any(), any()) } returns
            RetiroEfectivoListResponseDto(items = listOf(retiroDto("remoto-9", localId = null)), page = 1, pageSize = 100, total = 1)
        coEvery { dao.getRetiroByLocalId("remoto-9") } returns null

        RetiroPuller(api, dao, syncStateStore).pull("suc-1")

        coVerify { dao.insertRetiro(match { it.localId == "remoto-9" && it.remoteId == "remoto-9" }) }
    }
}
