package com.pdv.pos.data.local

import com.pdv.pos.domain.model.Devolucion
import com.pdv.pos.domain.model.DevolucionLinea
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertFailsWith

class LocalDevolucionRepositoryTest {

    private fun devolucionDeEjemplo() = Devolucion(
        id = "devolucion-1",
        sucursalId = "suc-1",
        usuarioId = "german",
        ventaId = "venta-1",
        folio = "D-001",
        fecha = 1_700_000_000_000L,
        estado = "registrada",
        lineas = listOf(
            DevolucionLinea(
                articuloId = "art-1",
                cantidad = BigDecimal("1"),
                motivo = "Producto dañado",
                condicion = "defectuoso",
            ),
        ),
    )

    @Test
    fun `registrarDevolucion inserts devolucion and detalle in one transaction and logs DB_WRITE`() = runTest {
        val dao = mockk<DevolucionDao>()
        val appLogger = mockk<AppLogger>()
        coEvery { dao.insertDevolucionCompleta(any(), any()) } returns Unit
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val repository = LocalDevolucionRepository(dao, appLogger)
        val devolucion = devolucionDeEjemplo()

        repository.registrarDevolucion(devolucion)

        coVerify {
            dao.insertDevolucionCompleta(
                devolucion = match { it.localId == "devolucion-1" && it.ventaId == "venta-1" && it.folio == "D-001" },
                detalles = match {
                    it.size == 1 &&
                        it.single().articuloId == "art-1" &&
                        it.single().condicion == "defectuoso" &&
                        it.single().motivo == "Producto dañado"
                },
            )
        }
        coVerify { appLogger.log(LogType.DB_WRITE, sucursalId = "suc-1", usuario = "german", mensaje = any()) }
    }

    @Test
    fun `registrarDevolucion propagates a DAO failure without logging DB_WRITE`() = runTest {
        val dao = mockk<DevolucionDao>()
        val appLogger = mockk<AppLogger>()
        coEvery { dao.insertDevolucionCompleta(any(), any()) } throws IllegalStateException("localId duplicado")
        val repository = LocalDevolucionRepository(dao, appLogger)

        assertFailsWith<IllegalStateException> { repository.registrarDevolucion(devolucionDeEjemplo()) }

        coVerify(exactly = 0) { appLogger.log(any(), any(), any(), any()) }
    }
}
