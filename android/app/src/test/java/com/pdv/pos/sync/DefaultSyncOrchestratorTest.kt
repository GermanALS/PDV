package com.pdv.pos.sync

import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.config.DeviceConfig
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.sync.pull.CorteCajaPuller
import com.pdv.pos.sync.pull.EntityPuller
import com.pdv.pos.sync.pull.InventarioPuller
import com.pdv.pos.sync.pull.RetiroPuller
import com.pdv.pos.sync.push.CorteCajaPusher
import com.pdv.pos.sync.push.DevolucionPusher
import com.pdv.pos.sync.push.EntradaPusher
import com.pdv.pos.sync.push.InventarioAjustePusher
import com.pdv.pos.sync.push.PushResultado
import com.pdv.pos.sync.push.RetiroPusher
import com.pdv.pos.sync.push.VentaPusher
import com.pdv.pos.sync.push.httpException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class DefaultSyncOrchestratorTest {

    private val entrada = mockk<EntradaPusher>()
    private val venta = mockk<VentaPusher>()
    private val ajuste = mockk<InventarioAjustePusher>()
    private val corte = mockk<CorteCajaPusher>()
    private val retiro = mockk<RetiroPusher>()
    private val devolucion = mockk<DevolucionPusher>()

    private val inventarioPuller = mockk<InventarioPuller>(relaxed = true)
    private val cortePuller = mockk<CorteCajaPuller>(relaxed = true)
    private val retiroPuller = mockk<RetiroPuller>(relaxed = true)

    private val preferences = mockk<ConfiguracionPreferences>()
    private val appLogger = mockk<AppLogger>(relaxed = true)

    private fun orchestrator(sucursalId: String? = "suc-1"): DefaultSyncOrchestrator {
        every { preferences.deviceConfig } returns flowOf(DeviceConfig(sucursalIdSeleccionada = sucursalId))
        return DefaultSyncOrchestrator(
            pushers = listOf(entrada, venta, ajuste, corte, retiro, devolucion),
            pullers = listOf<EntityPuller>(inventarioPuller, cortePuller, retiroPuller),
            preferences = preferences,
            appLogger = appLogger,
        )
    }

    private fun allPushersReturn(resultado: PushResultado) {
        listOf(entrada, venta, ajuste, corte, retiro, devolucion).forEach {
            coEvery { it.empujarPendientes() } returns resultado
        }
    }

    @Test
    fun `pushes in FK-safe order then pulls the selected sucursal and returns Exito`() = runTest {
        allPushersReturn(PushResultado(subidas = 1, saltadas = 0))

        val resultado = orchestrator(sucursalId = "suc-1").sincronizar()

        assertEquals(SyncResultado.Exito, resultado)
        coVerifyOrder {
            entrada.empujarPendientes()
            venta.empujarPendientes()
            ajuste.empujarPendientes()
            corte.empujarPendientes()
            retiro.empujarPendientes()
            devolucion.empujarPendientes()
            inventarioPuller.pull("suc-1")
            cortePuller.pull("suc-1")
            retiroPuller.pull("suc-1")
        }
    }

    @Test
    fun `skips the pull when no sucursal is selected`() = runTest {
        allPushersReturn(PushResultado.VACIO)

        val resultado = orchestrator(sucursalId = null).sincronizar()

        assertEquals(SyncResultado.Exito, resultado)
        coVerify(exactly = 0) { inventarioPuller.pull(any()) }
    }

    @Test
    fun `a network error from a pusher maps to Reintentar`() = runTest {
        allPushersReturn(PushResultado.VACIO)
        coEvery { venta.empujarPendientes() } throws IOException("sin red")

        assertTrue(orchestrator().sincronizar() is SyncResultado.Reintentar)
    }

    @Test
    fun `a network error from a puller maps to Reintentar`() = runTest {
        allPushersReturn(PushResultado.VACIO)
        coEvery { inventarioPuller.pull(any()) } throws IOException("sin red en el pull")

        assertTrue(orchestrator().sincronizar() is SyncResultado.Reintentar)
    }

    @Test
    fun `a 401 stops the cycle with Fallo`() = runTest {
        allPushersReturn(PushResultado.VACIO)
        coEvery { corte.empujarPendientes() } throws httpException(401)

        assertEquals(SyncResultado.Fallo("sesion expirada"), orchestrator().sincronizar())
    }

    @Test
    fun `a 5xx maps to Reintentar`() = runTest {
        allPushersReturn(PushResultado.VACIO)
        coEvery { devolucion.empujarPendientes() } throws httpException(503)

        assertTrue(orchestrator().sincronizar() is SyncResultado.Reintentar)
    }
}
