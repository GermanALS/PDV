package com.pdv.pos.sync

sealed class PlanMigracion {
    data class SubirLocal(val cantidadLocal: Int) : PlanMigracion()
    data class TraerRemoto(val cantidadRemota: Int) : PlanMigracion()
    data class RequiereConfirmacion(
        val cantidadLocal: Int,
        val cantidadRemota: Int,
        val mensajeImpacto: String,
    ) : PlanMigracion()
    data object SinDatos : PlanMigracion()
}
