package com.pdv.pos.inventario.importacion

import com.pdv.pos.domain.model.ArticuloNuevo
import com.pdv.pos.domain.model.Entrada
import com.pdv.pos.domain.repository.EntradaRepository
import com.pdv.pos.inventario.BuscadorArticuloExistente
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

data class ErrorImportacion(val numeroFila: Int, val motivo: String)

data class ResumenImportacion(
    val creados: Int = 0,
    val actualizados: Int = 0,
    val errores: List<ErrorImportacion> = emptyList(),
)

sealed class ResultadoImportacion {
    data object EncabezadoInvalido : ResultadoImportacion()
    data class Completado(val resumen: ResumenImportacion) : ResultadoImportacion()
}

// Logica de negocio de la importacion CSV (PLAN.md Parte 18, sub-parte A),
// separada de ImportacionCatalogoViewModel para poder probarla con
// contenido de texto plano en vez de un Uri/ContentResolver real (esos
// necesitan needs-device, igual que el resto de la integracion con
// Android). Cada fila valida reutiliza EntradaRepository.registrarEntrada
// con el mismo criterio de dedup que EjecutorAccionesIa (PLAN.md Parte 16).
class ImportadorCatalogo @Inject constructor(
    private val entradaRepository: EntradaRepository,
    private val buscadorArticuloExistente: BuscadorArticuloExistente,
) {
    suspend fun importar(
        contenido: String,
        sucursalId: String,
        usuarioId: String,
        onProgreso: (procesados: Int, total: Int) -> Unit = { _, _ -> },
    ): ResultadoImportacion {
        val parseo = InventarioCsvImporter.parsear(contenido)
        if (!parseo.encabezadoValido) return ResultadoImportacion.EncabezadoInvalido

        var creados = 0
        var actualizados = 0
        val errores = mutableListOf<ErrorImportacion>()

        parseo.filas.forEachIndexed { indice, fila ->
            when (fila) {
                is FilaCsvImportada.Invalida -> errores += ErrorImportacion(fila.numeroFila, fila.motivo)
                is FilaCsvImportada.Valida -> {
                    try {
                        if (procesarFila(fila.datos, sucursalId, usuarioId)) actualizados++ else creados++
                    } catch (e: IOException) {
                        errores += ErrorImportacion(indice + 2, e.message ?: "Sin conexión")
                    } catch (e: HttpException) {
                        errores += ErrorImportacion(indice + 2, e.message ?: "Error del servidor")
                    }
                }
            }
            onProgreso(indice + 1, parseo.filas.size)
        }

        return ResultadoImportacion.Completado(ResumenImportacion(creados, actualizados, errores))
    }

    // true si sumo cantidad a un articulo existente (actualizado), false si
    // dio de alta uno nuevo (creado) - el CSV no trae codigo de barras como
    // columna, asi que el dedup solo cae en nombre/sku exactos.
    private suspend fun procesarFila(datos: DatosFilaImportada, sucursalId: String, usuarioId: String): Boolean {
        val ahora = System.currentTimeMillis()
        val existente = buscadorArticuloExistente.buscar(sucursalId, datos.nombre, datos.sku, codigoBarras = null)
        if (existente != null) {
            entradaRepository.registrarEntrada(
                Entrada.DeArticuloExistente(
                    id = UUID.randomUUID().toString(),
                    sucursalId = sucursalId,
                    usuarioId = usuarioId,
                    fecha = ahora,
                    cantidad = datos.cantidad,
                    ubicacion = datos.ubicacion,
                    articuloId = existente.id,
                ),
            )
            return true
        }

        entradaRepository.registrarEntrada(
            Entrada.DeArticuloNuevo(
                id = UUID.randomUUID().toString(),
                sucursalId = sucursalId,
                usuarioId = usuarioId,
                fecha = ahora,
                cantidad = datos.cantidad,
                ubicacion = datos.ubicacion,
                articulo = ArticuloNuevo(
                    id = UUID.randomUUID().toString(),
                    codigoBarras = null,
                    sku = datos.sku,
                    nombre = datos.nombre,
                    descripcion = null,
                    categoria = datos.categoria,
                    unidadMedida = datos.unidadMedida,
                    precioVenta = datos.precioVenta,
                    costo = datos.costo,
                ),
            ),
        )
        return false
    }
}
