package com.pdv.pos.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MigrationPlannerTest {

    @Test
    fun `solo datos locales sube las sucursales locales`() {
        val plan = MigrationPlanner.planificar(sucursalesLocalesNoSincronizadas = 1, sucursalesRemotas = 0)

        val subirLocal = assertInstanceOf(PlanMigracion.SubirLocal::class.java, plan)
        assertEquals(1, subirLocal.cantidadLocal)
    }

    @Test
    fun `solo datos remotos trae las sucursales remotas`() {
        val plan = MigrationPlanner.planificar(sucursalesLocalesNoSincronizadas = 0, sucursalesRemotas = 3)

        val traerRemoto = assertInstanceOf(PlanMigracion.TraerRemoto::class.java, plan)
        assertEquals(3, traerRemoto.cantidadRemota)
    }

    @Test
    fun `datos en ambos lados requiere confirmacion del administrador`() {
        val plan = MigrationPlanner.planificar(sucursalesLocalesNoSincronizadas = 1, sucursalesRemotas = 2)

        assertInstanceOf(PlanMigracion.RequiereConfirmacion::class.java, plan)
    }

    @Test
    fun `el dialogo de confirmacion describe el impacto concreto de la migracion`() {
        val plan = MigrationPlanner.planificar(sucursalesLocalesNoSincronizadas = 2, sucursalesRemotas = 5)

        val requiereConfirmacion = assertInstanceOf(PlanMigracion.RequiereConfirmacion::class.java, plan)
        assertEquals(2, requiereConfirmacion.cantidadLocal)
        assertEquals(5, requiereConfirmacion.cantidadRemota)
        assertTrue(requiereConfirmacion.mensajeImpacto.contains("2"))
        assertTrue(requiereConfirmacion.mensajeImpacto.contains("5"))
    }
}
