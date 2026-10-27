package com.pdv.pos.acercade

/** Release and development-team information shown in "Acerca de". */
data class InfoRelease(
    val versionName: String,
    val versionCode: Int,
    val fecha: String,
    val novedades: List<String>,
    val limitaciones: List<String>,
    val equipo: String,
    val contacto: String,
)
