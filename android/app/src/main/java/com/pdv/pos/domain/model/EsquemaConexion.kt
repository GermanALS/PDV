package com.pdv.pos.domain.model

// Esquema de la URL del backend remoto. Preferencia de dispositivo en
// DataStore, igual que BackendMode/ip/puerto (CLAUDE.md sec. 3). HTTP es el
// valor por defecto: el camino Wi-Fi LAN del build de debug usa cleartext
// (PLAN.md Parte 31); HTTPS queda disponible para un backend productivo con
// certificado valido (PLAN.md Parte 32).
enum class EsquemaConexion {
    HTTP,
    HTTPS;

    fun scheme(): String = name.lowercase()
}
