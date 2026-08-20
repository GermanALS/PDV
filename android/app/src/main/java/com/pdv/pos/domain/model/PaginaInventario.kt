package com.pdv.pos.domain.model

// Resultado paginado real (PLAN.md Parte 9, sub-paso 2) - reemplaza el
// recorte en memoria sobre el catalogo estatico del sub-paso 1.
data class PaginaInventario(
    val items: List<InventarioItem>,
    val pagina: Int,
    val tamanioPagina: Int,
    val total: Int,
) {
    val totalPaginas: Int
        get() = maxOf(1, (total + tamanioPagina - 1) / tamanioPagina)
}
