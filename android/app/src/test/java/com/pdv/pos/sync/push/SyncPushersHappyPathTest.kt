package com.pdv.pos.sync.push

import com.pdv.pos.data.local.CajaDao
import com.pdv.pos.data.local.CorteCajaEntity
import com.pdv.pos.data.local.DevolucionDao
import com.pdv.pos.data.local.DevolucionEntity
import com.pdv.pos.data.local.RetiroDao
import com.pdv.pos.data.local.RetiroEfectivoEntity
import com.pdv.pos.data.remote.CajaApiService
import com.pdv.pos.data.remote.DevolucionApiService
import com.pdv.pos.data.remote.RetiroApiService
import com.pdv.pos.data.remote.dto.CorteCajaDto
import com.pdv.pos.data.remote.dto.DevolucionDto
import com.pdv.pos.data.remote.dto.RetiroEfectivoDto
import com.pdv.pos.logging.AppLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

// Cortes, retiros y devoluciones comparten la forma del VentaPusher sin la
// complejidad del articulo; un happy path por cada uno alcanza (el resto de
// las ramas -4xx, IOException, 401- estan cubiertas en VentaPusherTest, que
// ejercita el helper compartido empujarFila).
class SyncPushersHappyPathTest {

    private val appLogger = mockk<AppLogger>(relaxed = true)

    @Test
    fun `CorteCajaPusher marks the corte synced with the remote id`() = runTest {
        val dao = mockk<CajaDao>(relaxUnitFun = true)
        val api = mockk<CajaApiService>()
        coEvery { dao.getCortesPendientes() } returns listOf(
            CorteCajaEntity(
                localId = "corte-1", remoteId = null, sucursalId = "suc-1", usuarioId = "u-1", tipo = "final",
                fechaInicio = 1L, fechaFin = 2L, totalVentas = BigDecimal.ZERO, totalEfectivo = BigDecimal.ZERO,
                totalTarjeta = BigDecimal.ZERO, totalRetiros = BigDecimal.ZERO, montoEsperado = BigDecimal.ZERO,
                montoContado = null, diferencia = null, updatedAt = 0L, isSynced = false, deletedAt = null,
            ),
        )
        coEvery { api.createCorte(any()) } returns CorteCajaDto(
            id = "corte-remoto", localId = "corte-1", sucursalId = "suc-1", usuarioId = "u-1", tipo = "final",
            fechaInicio = "2023-11-14T22:13:20Z", fechaFin = "2023-11-14T22:13:20Z", totalVentas = "0",
            totalEfectivo = "0", totalTarjeta = "0", totalRetiros = "0", montoEsperado = "0",
            updatedAt = "2023-11-14T22:13:20Z",
        )

        val resultado = CorteCajaPusher(dao, api, appLogger).empujarPendientes()

        assertEquals(PushResultado(subidas = 1, saltadas = 0), resultado)
        coVerify { dao.marcarCorteSincronizado("corte-1", "corte-remoto") }
    }

    @Test
    fun `RetiroPusher marks the retiro synced with the remote id`() = runTest {
        val dao = mockk<RetiroDao>(relaxUnitFun = true)
        val api = mockk<RetiroApiService>()
        coEvery { dao.getRetirosPendientes() } returns listOf(
            RetiroEfectivoEntity(
                localId = "retiro-1", remoteId = null, sucursalId = "suc-1", usuarioId = "u-1",
                monto = BigDecimal("50"), motivo = "gasto", fecha = 1L, updatedAt = 0L, isSynced = false, deletedAt = null,
            ),
        )
        coEvery { api.createRetiro(any()) } returns RetiroEfectivoDto(
            id = "retiro-remoto", localId = "retiro-1", sucursalId = "suc-1", usuarioId = "u-1", monto = "50",
            motivo = "gasto", fecha = "2023-11-14T22:13:20Z", updatedAt = "2023-11-14T22:13:20Z",
        )

        val resultado = RetiroPusher(dao, api, appLogger).empujarPendientes()

        assertEquals(PushResultado(subidas = 1, saltadas = 0), resultado)
        coVerify { dao.marcarRetiroSincronizado("retiro-1", "retiro-remoto") }
    }

    @Test
    fun `DevolucionPusher marks the devolucion and its detalles synced`() = runTest {
        val dao = mockk<DevolucionDao>(relaxUnitFun = true)
        val api = mockk<DevolucionApiService>()
        coEvery { dao.getDevolucionesPendientes() } returns listOf(
            DevolucionEntity(
                localId = "dev-1", remoteId = null, sucursalId = "suc-1", usuarioId = "u-1", ventaId = "v-1",
                folio = "D-001", fecha = 1L, estado = "pendiente", updatedAt = 0L, isSynced = false, deletedAt = null,
            ),
        )
        coEvery { dao.getDetallesDeDevolucion("dev-1") } returns emptyList()
        coEvery { dao.getVentaRemoteId("v-1") } returns "venta-remoto"
        coEvery { api.createDevolucion(any()) } returns DevolucionDto(
            id = "dev-remoto", localId = "dev-1", sucursalId = "suc-1", usuarioId = "u-1", ventaId = "v-1",
            folio = "D-001", fecha = "2023-11-14T22:13:20Z", estado = "pendiente",
            updatedAt = "2023-11-14T22:13:20Z", lineas = emptyList(),
        )

        val resultado = DevolucionPusher(dao, api, appLogger).empujarPendientes()

        assertEquals(PushResultado(subidas = 1, saltadas = 0), resultado)
        coVerify { dao.marcarDevolucionSincronizada("dev-1", "dev-remoto") }
        coVerify { dao.marcarDetallesDevolucionSincronizados("dev-1") }
    }
}
