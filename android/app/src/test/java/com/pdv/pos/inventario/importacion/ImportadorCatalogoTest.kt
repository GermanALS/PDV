package com.pdv.pos.inventario.importacion

import com.pdv.pos.domain.model.Articulo
import com.pdv.pos.domain.model.Entrada
import com.pdv.pos.domain.model.InventarioItem
import com.pdv.pos.domain.model.PaginaInventario
import com.pdv.pos.domain.repository.EntradaRepository
import com.pdv.pos.domain.repository.InventarioRepository
import com.pdv.pos.inventario.BuscadorArticuloExistente
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ImportadorCatalogoTest {

    private val encabezado = "SKU,Nombre,Categoria,Unidad de medida,Cantidad,Ubicacion,Precio de venta,Costo"

    private fun inventarioRepositoryConArticulos(articulos: List<Articulo>): InventarioRepository {
        val repo = mockk<InventarioRepository>()
        every { repo.observarInventario(any(), any(), any(), any()) } answers {
            val termino = secondArg<String>()
            val encontrados = articulos.filter {
                it.nombre.contains(termino, ignoreCase = true) || it.sku.contains(termino, ignoreCase = true)
            }
            flowOf(
                PaginaInventario(
                    items = encontrados.map { InventarioItem(it, BigDecimal.ZERO, null) },
                    pagina = 1,
                    tamanioPagina = 20,
                    total = encontrados.size,
                ),
            )
        }
        return repo
    }

    private fun importador(
        entradaRepository: EntradaRepository = mockk(relaxed = true),
        articulosExistentes: List<Articulo> = emptyList(),
    ) = ImportadorCatalogo(entradaRepository, BuscadorArticuloExistente(inventarioRepositoryConArticulos(articulosExistentes)))

    @Test
    fun `csv con encabezado invalido devuelve EncabezadoInvalido sin llamar al repositorio`() = runTest {
        val entradaRepository = mockk<EntradaRepository>()

        val resultado = importador(entradaRepository).importar("A,B,C\n1,2,3", "suc-1", "german")

        assertTrue(resultado is ResultadoImportacion.EncabezadoInvalido)
        coVerify(exactly = 0) { entradaRepository.registrarEntrada(any()) }
    }

    @Test
    fun `filas validas se dan de alta y las invalidas quedan en el resumen de errores`() = runTest {
        val entradaRepository = mockk<EntradaRepository>()
        coEvery { entradaRepository.registrarEntrada(any()) } returns Unit
        val csv = """
            $encabezado
            REF-001,Refresco de cola,Bebidas,pieza,10,Estante A1,18.50,12.00
            PAN-002,Pan integral,Panaderia,pieza,texto,Estante B2,42.00,
        """.trimIndent()

        val resultado = importador(entradaRepository).importar(csv, "suc-1", "german") as ResultadoImportacion.Completado

        assertEquals(1, resultado.resumen.creados)
        assertEquals(0, resultado.resumen.actualizados)
        assertEquals(1, resultado.resumen.errores.size)
        assertEquals(3, resultado.resumen.errores.single().numeroFila)
        coVerify(exactly = 1) { entradaRepository.registrarEntrada(any()) }
    }

    // Hallazgo de importar un articulo ya presente en el catalogo: la
    // ocurrencia del CSV debe sumar cantidad al existente, no duplicar el
    // articulo (mismo criterio de dedup que EjecutorAccionesIa, PLAN.md
    // Parte 16, ahora compartido via BuscadorArticuloExistente).
    @Test
    fun `un articulo ya existente en el catalogo suma cantidad en vez de duplicarse`() = runTest {
        val entradaRepository = mockk<EntradaRepository>()
        coEvery { entradaRepository.registrarEntrada(any()) } returns Unit
        val existente = Articulo(
            id = "art-existente",
            sku = "REF-001",
            nombre = "Refresco de cola",
            unidadMedida = "pieza",
            precioVenta = BigDecimal("18.50"),
        )
        val csv = "$encabezado\nREF-001,Refresco de cola,Bebidas,pieza,10,Estante A1,18.50,12.00"

        val resultado = importador(entradaRepository, listOf(existente)).importar(csv, "suc-1", "german")
            as ResultadoImportacion.Completado

        assertEquals(0, resultado.resumen.creados)
        assertEquals(1, resultado.resumen.actualizados)
        coVerify {
            entradaRepository.registrarEntrada(
                match {
                    it is Entrada.DeArticuloExistente && it.articuloId == "art-existente" && it.cantidad == BigDecimal("10")
                },
            )
        }
    }

    @Test
    fun `reporta progreso por cada fila procesada`() = runTest {
        val entradaRepository = mockk<EntradaRepository>(relaxed = true)
        val csv = """
            $encabezado
            REF-001,Refresco,Bebidas,pieza,10,Estante A1,18.50,
            PAN-002,Pan,Panaderia,pieza,5,Estante B2,42.00,
        """.trimIndent()
        val progreso = mutableListOf<Pair<Int, Int>>()

        importador(entradaRepository).importar(csv, "suc-1", "german") { procesados, total ->
            progreso += procesados to total
        }

        assertEquals(listOf(1 to 2, 2 to 2), progreso)
    }
}
