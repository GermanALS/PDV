package com.pdv.pos.inventario

import com.pdv.pos.domain.model.Articulo
import com.pdv.pos.domain.repository.InventarioRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

// Extraido de EjecutorAccionesIa (PLAN.md Parte 18, sub-parte A) para
// reutilizar el mismo criterio de dedup en la importacion CSV: nombre exacto
// -> sku exacto -> codigo de barras exacto, cada uno con su propia consulta
// (una consulta por termino, no un unico candidato reusado entre nombre/sku/
// codigo - ver EjecutorAccionesIaIntegrationTest para el hallazgo de
// code-reviewer que motiva esto).
class BuscadorArticuloExistente @Inject constructor(
    private val inventarioRepository: InventarioRepository,
) {
    suspend fun buscar(sucursalId: String, nombre: String, sku: String, codigoBarras: String?): Articulo? {
        buscarCoincidenciaExacta(sucursalId, nombre) { it.nombre.trim().equals(nombre.trim(), ignoreCase = true) }
            ?.let { return it }
        buscarCoincidenciaExacta(sucursalId, sku) { it.sku.equals(sku, ignoreCase = true) }
            ?.let { return it }
        val codigo = codigoBarras ?: return null
        return buscarCoincidenciaExacta(sucursalId, codigo) { it.codigoBarras == codigo }
    }

    private suspend fun buscarCoincidenciaExacta(
        sucursalId: String,
        termino: String,
        coincide: (Articulo) -> Boolean,
    ): Articulo? = inventarioRepository
        .observarInventario(sucursalId, busqueda = termino, pagina = 1, tamanioPagina = 20)
        .first()
        .items
        .map { it.articulo }
        .find(coincide)
}
