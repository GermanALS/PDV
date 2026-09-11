package com.pdv.pos.sync

import com.pdv.pos.sync.push.EntityPusher
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncPendientesResumenTest {

    private fun pusher(nombre: String, pendientes: Int) = mockk<EntityPusher> {
        every { this@mockk.nombre } returns nombre
        coEvery { contarPendientes() } returns pendientes
    }

    @Test
    fun `lists only the entities with pending rows and sums the total`() = runTest {
        val resumen = SyncPendientesResumen(
            listOf(pusher("entradas", 0), pusher("ventas", 3), pusher("cortes de caja", 1)),
        ).calcular()

        assertEquals(listOf("ventas" to 3, "cortes de caja" to 1), resumen.porEntidad)
        assertEquals(4, resumen.total)
        assertTrue(resumen.hayPendientes)
    }

    @Test
    fun `reports nothing pending when every pusher is empty`() = runTest {
        val resumen = SyncPendientesResumen(listOf(pusher("ventas", 0), pusher("entradas", 0))).calcular()

        assertEquals(emptyList<Pair<String, Int>>(), resumen.porEntidad)
        assertFalse(resumen.hayPendientes)
    }
}
