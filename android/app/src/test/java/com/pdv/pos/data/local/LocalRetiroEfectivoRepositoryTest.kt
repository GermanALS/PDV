package com.pdv.pos.data.local

import com.pdv.pos.domain.model.RetiroEfectivo
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertFailsWith

class LocalRetiroEfectivoRepositoryTest {

    private fun retiroDeEjemplo() = RetiroEfectivo(
        id = "retiro-1",
        sucursalId = "suc-1",
        usuarioId = "german",
        monto = BigDecimal("100.00"),
        motivo = "Pago a proveedor",
        fecha = 1_700_000_000_000L,
    )

    @Test
    fun `registrarRetiro inserts retiro and logs DB_WRITE`() = runTest {
        val dao = mockk<RetiroDao>()
        val appLogger = mockk<AppLogger>()
        coEvery { dao.insertRetiro(any()) } returns Unit
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = LocalRetiroEfectivoRepository(dao, appLogger)

        repository.registrarRetiro(retiroDeEjemplo())

        coVerify {
            dao.insertRetiro(
                match { it.localId == "retiro-1" && it.monto == BigDecimal("100.00") && it.motivo == "Pago a proveedor" },
            )
        }
        coVerify { appLogger.log(LogType.DB_WRITE, sucursalId = "suc-1", usuario = "german", mensaje = any()) }
    }

    @Test
    fun `registrarRetiro propagates a DAO failure without logging DB_WRITE`() = runTest {
        val dao = mockk<RetiroDao>()
        val appLogger = mockk<AppLogger>()
        coEvery { dao.insertRetiro(any()) } throws IllegalStateException("localId duplicado")
        val repository = LocalRetiroEfectivoRepository(dao, appLogger)

        assertFailsWith<IllegalStateException> { repository.registrarRetiro(retiroDeEjemplo()) }

        coVerify(exactly = 0) { appLogger.log(any(), any(), any(), any()) }
    }
}
