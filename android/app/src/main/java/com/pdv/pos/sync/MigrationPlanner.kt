package com.pdv.pos.sync

// Los 4 casos de migracion de datos al cambiar de modo (PLAN.md Parte 6):
// solo local, solo remoto, ambos lados (requiere confirmacion, sin merge
// automatico de duplicados en el MVP - PLAN.md Parte 3, "Sucursal por
// defecto"), y el dialogo con el impacto concreto de ese ultimo caso.
object MigrationPlanner {
    fun planificar(sucursalesLocalesNoSincronizadas: Int, sucursalesRemotas: Int): PlanMigracion = when {
        sucursalesLocalesNoSincronizadas > 0 && sucursalesRemotas == 0 ->
            PlanMigracion.SubirLocal(sucursalesLocalesNoSincronizadas)
        sucursalesLocalesNoSincronizadas == 0 && sucursalesRemotas > 0 ->
            PlanMigracion.TraerRemoto(sucursalesRemotas)
        sucursalesLocalesNoSincronizadas > 0 && sucursalesRemotas > 0 ->
            PlanMigracion.RequiereConfirmacion(
                cantidadLocal = sucursalesLocalesNoSincronizadas,
                cantidadRemota = sucursalesRemotas,
                mensajeImpacto = "Hay $sucursalesLocalesNoSincronizadas sucursal(es) local(es) sin sincronizar " +
                    "y $sucursalesRemotas sucursal(es) en el servidor. No se combinan automaticamente: " +
                    "continuar subira las locales como nuevas, sin relacionarlas con las remotas existentes.",
            )
        else -> PlanMigracion.SinDatos
    }
}
