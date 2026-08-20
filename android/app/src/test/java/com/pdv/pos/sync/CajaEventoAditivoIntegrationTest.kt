package com.pdv.pos.sync

import com.pdv.pos.data.local.CajaDao
import com.pdv.pos.data.local.CorteCajaEntity
import com.pdv.pos.data.local.LocalCajaRepository
import com.pdv.pos.data.local.LocalRetiroEfectivoRepository
import com.pdv.pos.data.local.RetiroDao
import com.pdv.pos.data.local.RetiroEfectivoEntity
import com.pdv.pos.data.local.VentaDao
import com.pdv.pos.domain.model.CorteCaja
import com.pdv.pos.domain.model.RetiroEfectivo
import com.pdv.pos.logging.AppLogger
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

// Cierra "los cortes de caja y los retiros de efectivo se tratan como
// eventos aditivos" (PLAN.md Parte 10, checklist "Wiring"). A diferencia
// de inventario.cantidad (EventoAditivoCombiner, delta con signo sobre un
// campo compartido, ver VentaEventoAditivoIntegrationTest), aqui "aditivo"
// es mas simple: cada corte/retiro es su propio registro con local_id
// generado por dispositivo (UUID), asi que "ambos se aplican" significa
// que dos dispositivos insertando concurrentemente nunca se pisan entre
// si - no hay ningun campo compartido que combinar ni conflicto que
// resolver.
class CajaEventoAditivoIntegrationTest {

    private fun corteDeEjemplo(id: String, usuarioId: String) = CorteCaja(
        id = id,
        sucursalId = "suc-1",
        usuarioId = usuarioId,
        tipo = "parcial",
        fechaInicio = 1_700_000_000_000L,
        fechaFin = 1_700_003_600_000L,
        totalVentas = BigDecimal("100.00"),
        totalEfectivo = BigDecimal("100.00"),
        totalTarjeta = BigDecimal.ZERO,
        totalRetiros = BigDecimal.ZERO,
        montoEsperado = BigDecimal("100.00"),
        montoContado = null,
        diferencia = null,
    )

    private fun retiroDeEjemplo(id: String, usuarioId: String) = RetiroEfectivo(
        id = id,
        sucursalId = "suc-1",
        usuarioId = usuarioId,
        monto = BigDecimal("50.00"),
        motivo = null,
        fecha = 1_700_000_000_000L,
    )

    @Test
    fun `two devices saving a corte concurrently are both inserted, neither overwrites the other`() = runTest {
        val cajaDao = mockk<CajaDao>()
        val ventaDao = mockk<VentaDao>()
        val retiroDao = mockk<RetiroDao>()
        val appLogger = mockk<AppLogger>()
        val insertados = mutableListOf<CorteCajaEntity>()
        coEvery { cajaDao.insertCorte(capture(insertados)) } returns Unit
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = LocalCajaRepository(cajaDao, ventaDao, retiroDao, appLogger)

        repository.guardarCorte(corteDeEjemplo(id = "corte-dispositivo-a", usuarioId = "admin"))
        repository.guardarCorte(corteDeEjemplo(id = "corte-dispositivo-b", usuarioId = "user1"))

        assertEquals(2, insertados.size)
        assertEquals(setOf("corte-dispositivo-a", "corte-dispositivo-b"), insertados.map { it.localId }.toSet())
    }

    @Test
    fun `two devices registering a retiro concurrently are both inserted, neither overwrites the other`() = runTest {
        val dao = mockk<RetiroDao>()
        val appLogger = mockk<AppLogger>()
        val insertados = mutableListOf<RetiroEfectivoEntity>()
        coEvery { dao.insertRetiro(capture(insertados)) } returns Unit
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = LocalRetiroEfectivoRepository(dao, appLogger)

        repository.registrarRetiro(retiroDeEjemplo(id = "retiro-dispositivo-a", usuarioId = "admin"))
        repository.registrarRetiro(retiroDeEjemplo(id = "retiro-dispositivo-b", usuarioId = "user1"))

        assertEquals(2, insertados.size)
        assertEquals(setOf("retiro-dispositivo-a", "retiro-dispositivo-b"), insertados.map { it.localId }.toSet())
    }
}
