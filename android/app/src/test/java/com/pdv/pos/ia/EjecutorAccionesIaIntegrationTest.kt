package com.pdv.pos.ia

import com.pdv.pos.domain.model.Articulo
import com.pdv.pos.domain.model.Entrada
import com.pdv.pos.domain.model.InventarioItem
import com.pdv.pos.domain.model.PaginaInventario
import com.pdv.pos.domain.model.TotalesCorte
import com.pdv.pos.domain.repository.CajaRepository
import com.pdv.pos.domain.repository.DevolucionRepository
import com.pdv.pos.domain.repository.EntradaRepository
import com.pdv.pos.domain.repository.InventarioRepository
import com.pdv.pos.domain.repository.RetiroEfectivoRepository
import com.pdv.pos.logging.AppLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.math.BigDecimal

// Cierra "Cada accion se valida contra los permisos reales del usuario en
// turno antes de ejecutarse" y "Acciones rechazadas se registran AUTH y se
// informan en el chat" (PLAN.md Parte 15, sub-paso 3): usa un AppLogger real
// (escribe a un archivo temporal) para confirmar el formato/categoria real
// del log, y repositorios mockeados para confirmar que solo la accion
// permitida efectivamente llama al repositorio real - la rechazada no.
class EjecutorAccionesIaIntegrationTest {

    private val json = Json { ignoreUnknownKeys = true }

    // Vacio por defecto: la mayoria de los tests de este archivo asumen
    // "articulo nuevo, sin coincidencia previa" - la busqueda de duplicados
    // (hallazgo de pruebas en el Xiaomi) solo se ejercita explicitamente en
    // los tests que le pasan un inventarioRepository con candidatos.
    private fun inventarioRepositorySinCoincidencias(): InventarioRepository =
        inventarioRepositoryConArticulos(emptyList())

    // Filtra por nombre/sku/codigoBarras segun el termino real recibido,
    // mismo criterio LIKE que InventarioDao.observarPagina - respeta el
    // argumento de busqueda en vez de ignorarlo con any(), para que la
    // prueba ejerza el mismo contrato que buscarArticuloExistente() usa en
    // produccion (una consulta por termino, no un unico candidato reusado
    // entre nombre/sku/codigo).
    private fun inventarioRepositoryConArticulos(articulos: List<Articulo>): InventarioRepository {
        val repo = mockk<InventarioRepository>()
        every { repo.observarInventario(any(), any(), any(), any()) } answers {
            val termino = secondArg<String>()
            val encontrados = articulos.filter {
                it.nombre.contains(termino, ignoreCase = true) ||
                    it.sku.contains(termino, ignoreCase = true) ||
                    (it.codigoBarras?.contains(termino, ignoreCase = true) == true)
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

    private fun ejecutor(
        entradaRepository: EntradaRepository = mockk(relaxed = true),
        cajaRepository: CajaRepository = mockk(relaxed = true),
        retiroEfectivoRepository: RetiroEfectivoRepository = mockk(relaxed = true),
        devolucionRepository: DevolucionRepository = mockk(relaxed = true),
        inventarioRepository: InventarioRepository = inventarioRepositorySinCoincidencias(),
        appLogger: AppLogger,
    ) = EjecutorAccionesIa(
        entradaRepository,
        cajaRepository,
        retiroEfectivoRepository,
        devolucionRepository,
        inventarioRepository,
        appLogger,
        json,
    )

    @Test
    fun `rechazo selectivo - una accion se ejecuta y otra de la misma respuesta se rechaza por falta de permiso`(
        @TempDir tempDir: File,
    ) = runTest {
        val appLogger = AppLogger(tempDir)
        val retiroRepository = mockk<RetiroEfectivoRepository>()
        coEvery { retiroRepository.registrarRetiro(any()) } returns Unit

        val ejecutor = ejecutor(retiroEfectivoRepository = retiroRepository, appLogger = appLogger)

        // Misma respuesta de la IA: corte_parcial (modulo "caja", sin permiso)
        // + retiro_efectivo (modulo "caja" tambien, pero en este caso el
        // usuario solo tiene "entrada" - ambas acciones deberian rechazarse
        // de forma independiente entre si si faltara el permiso de alguna,
        // pero aca ambas comparten modulo para probar el caso simple primero.
        val accionCorte = AccionIaDto(modulo = "caja", tipo = "corte_parcial", parametros = buildJsonObject { })
        val accionRetiro = AccionIaDto(
            modulo = "caja",
            tipo = "retiro_efectivo",
            parametros = buildJsonObject { put("monto", "500") },
        )
        val accionAltaSinPermiso = AccionIaDto(
            modulo = "entrada",
            tipo = "alta_articulo",
            parametros = buildJsonObject {
                put("sku", "TOR-001")
                put("nombre", "Tornillo")
                put("unidadMedida", "pieza")
                put("cantidad", "10")
                put("precioVenta", "5.00")
            },
        )

        // El usuario en turno solo tiene permiso de "caja", no de "entrada".
        val modulosPermitidos = setOf("caja")

        val resultadoCorte = ejecutor.ejecutar(accionCorte, modulosPermitidos, "suc-1", "german")
        val resultadoRetiro = ejecutor.ejecutar(accionRetiro, modulosPermitidos, "suc-1", "german")
        val resultadoAlta = ejecutor.ejecutar(accionAltaSinPermiso, modulosPermitidos, "suc-1", "german")

        assertTrue(resultadoCorte is ResultadoAccionIa.Ejecutada)
        assertTrue(resultadoRetiro is ResultadoAccionIa.Ejecutada)
        assertTrue(resultadoAlta is ResultadoAccionIa.RechazadaPorPermiso)
        coVerify { retiroRepository.registrarRetiro(any()) }

        val logContent = tempDir.listFiles()!!.single().readText()
        assertTrue(logContent.contains("[AUTH]"))
        assertTrue(logContent.contains("modulo 'entrada'"))
        assertTrue(logContent.contains("suc-1"))
        assertTrue(logContent.contains("german"))
    }

    @Test
    fun `accion ejecutada llama al repositorio real del modulo correspondiente`(@TempDir tempDir: File) = runTest {
        val cajaRepository = mockk<CajaRepository>()
        coEvery { cajaRepository.calcularTotales(any(), any(), any()) } returns TotalesCorte(
            totalVentas = BigDecimal("100.00"),
            totalEfectivo = BigDecimal("100.00"),
            totalTarjeta = BigDecimal.ZERO,
            totalRetiros = BigDecimal.ZERO,
            montoEsperado = BigDecimal("100.00"),
        )
        coEvery { cajaRepository.guardarCorte(any()) } returns Unit

        val ejecutor = ejecutor(cajaRepository = cajaRepository, appLogger = AppLogger(tempDir))
        val accion = AccionIaDto(modulo = "caja", tipo = "corte_parcial", parametros = buildJsonObject { })

        val resultado = ejecutor.ejecutar(accion, setOf("caja"), "suc-1", "german")

        assertTrue(resultado is ResultadoAccionIa.Ejecutada)
        coVerify {
            cajaRepository.calcularTotales(sucursalId = "suc-1", fechaInicio = any(), fechaFin = any())
            cajaRepository.guardarCorte(match { it.montoEsperado == BigDecimal("100.00") && it.usuarioId == "german" })
        }
    }

    @Test
    fun `una accion rechazada no llama a ningun repositorio de escritura`(@TempDir tempDir: File) = runTest {
        val devolucionRepository = mockk<DevolucionRepository>()
        val ejecutor = ejecutor(devolucionRepository = devolucionRepository, appLogger = AppLogger(tempDir))
        val accion = AccionIaDto(
            modulo = "devoluciones",
            tipo = "registrar_devolucion",
            parametros = buildJsonObject {
                put("articuloId", "art-1")
                put("cantidad", "1")
            },
        )

        val resultado = ejecutor.ejecutar(accion, modulosPermitidos = emptySet(), "suc-1", "german")

        assertTrue(resultado is ResultadoAccionIa.RechazadaPorPermiso)
        assertTrue((resultado as ResultadoAccionIa.RechazadaPorPermiso).mensaje.contains("devoluciones"))
        coVerify(exactly = 0) { devolucionRepository.registrarDevolucion(any()) }
    }

    @Test
    fun `una accion con parametros invalidos falla sin crashear`(@TempDir tempDir: File) = runTest {
        val ejecutor = ejecutor(appLogger = AppLogger(tempDir))
        val accion = AccionIaDto(
            modulo = "entrada",
            tipo = "alta_articulo",
            parametros = buildJsonObject { put("sku", "TOR-001") },
        )

        val resultado = ejecutor.ejecutar(accion, setOf("entrada"), "suc-1", "german")

        assertTrue(resultado is ResultadoAccionIa.Fallida)
    }

    // Hallazgo critico de code-reviewer: accion.modulo y accion.tipo vienen
    // de la misma fuente no confiable (el LLM) y nada los mantiene
    // consistentes entre si. El permiso siempre debe evaluarse contra el
    // modulo real de accion.tipo (mapeo fijo en codigo), nunca contra
    // accion.modulo, para que una respuesta con los campos cruzados no
    // saltee el permiso real.
    @Test
    fun `el modulo declarado por la IA se ignora para autorizar - se usa el modulo real del tipo`(@TempDir tempDir: File) = runTest {
        val cajaRepository = mockk<CajaRepository>()
        val ejecutor = ejecutor(cajaRepository = cajaRepository, appLogger = AppLogger(tempDir))

        // modulo="entrada" (el usuario SI tiene ese permiso) pero
        // tipo="corte_parcial" (accion real de "caja", que el usuario NO
        // tiene) - si el chequeo confiara en accion.modulo, esto se
        // ejecutaria indebidamente.
        val accion = AccionIaDto(modulo = "entrada", tipo = "corte_parcial", parametros = buildJsonObject { })

        val resultado = ejecutor.ejecutar(accion, modulosPermitidos = setOf("entrada"), "suc-1", "german")

        assertTrue(resultado is ResultadoAccionIa.RechazadaPorPermiso)
        assertTrue((resultado as ResultadoAccionIa.RechazadaPorPermiso).mensaje.contains("caja"))
        coVerify(exactly = 0) { cajaRepository.guardarCorte(any()) }
        val logContent = tempDir.listFiles()!!.single().readText()
        assertTrue(logContent.contains("modulo 'caja'"))
    }

    @Test
    fun `retiro_efectivo ejecutado construye RetiroEfectivo con los campos correctos`(@TempDir tempDir: File) = runTest {
        val retiroRepository = mockk<RetiroEfectivoRepository>()
        coEvery { retiroRepository.registrarRetiro(any()) } returns Unit
        val ejecutor = ejecutor(retiroEfectivoRepository = retiroRepository, appLogger = AppLogger(tempDir))
        val accion = AccionIaDto(
            modulo = "caja",
            tipo = "retiro_efectivo",
            parametros = buildJsonObject {
                put("monto", "500.00")
                put("motivo", "deposito bancario")
            },
        )

        val resultado = ejecutor.ejecutar(accion, setOf("caja"), "suc-1", "german")

        assertTrue(resultado is ResultadoAccionIa.Ejecutada)
        coVerify {
            retiroRepository.registrarRetiro(
                match {
                    it.monto == BigDecimal("500.00") &&
                        it.motivo == "deposito bancario" &&
                        it.sucursalId == "suc-1" &&
                        it.usuarioId == "german"
                },
            )
        }
    }

    @Test
    fun `retiro_efectivo con monto menor o igual a 0 se rechaza sin llamar al repositorio`(@TempDir tempDir: File) = runTest {
        val retiroRepository = mockk<RetiroEfectivoRepository>()
        val ejecutor = ejecutor(retiroEfectivoRepository = retiroRepository, appLogger = AppLogger(tempDir))
        val accion = AccionIaDto(
            modulo = "caja",
            tipo = "retiro_efectivo",
            parametros = buildJsonObject { put("monto", "0") },
        )

        val resultado = ejecutor.ejecutar(accion, setOf("caja"), "suc-1", "german")

        assertTrue(resultado is ResultadoAccionIa.Fallida)
        coVerify(exactly = 0) { retiroRepository.registrarRetiro(any()) }
    }

    @Test
    fun `alta_articulo con cantidad menor o igual a 0 se rechaza sin llamar al repositorio`(@TempDir tempDir: File) = runTest {
        val entradaRepository = mockk<EntradaRepository>()
        val ejecutor = ejecutor(entradaRepository = entradaRepository, appLogger = AppLogger(tempDir))
        val accion = AccionIaDto(
            modulo = "entrada",
            tipo = "alta_articulo",
            parametros = buildJsonObject {
                put("sku", "TOR-001")
                put("nombre", "Tornillo")
                put("unidadMedida", "pieza")
                put("cantidad", "0")
                put("precioVenta", "5.00")
            },
        )

        val resultado = ejecutor.ejecutar(accion, setOf("entrada"), "suc-1", "german")

        assertTrue(resultado is ResultadoAccionIa.Fallida)
        coVerify(exactly = 0) { entradaRepository.registrarEntrada(any()) }
    }

    @Test
    fun `registrar_devolucion con cantidad menor o igual a 0 se rechaza sin llamar al repositorio`(@TempDir tempDir: File) = runTest {
        val devolucionRepository = mockk<DevolucionRepository>()
        val ejecutor = ejecutor(devolucionRepository = devolucionRepository, appLogger = AppLogger(tempDir))
        val accion = AccionIaDto(
            modulo = "devoluciones",
            tipo = "registrar_devolucion",
            parametros = buildJsonObject {
                put("articuloId", "art-1")
                put("cantidad", "0")
            },
        )

        val resultado = ejecutor.ejecutar(accion, setOf("devoluciones"), "suc-1", "german")

        assertTrue(resultado is ResultadoAccionIa.Fallida)
        coVerify(exactly = 0) { devolucionRepository.registrarDevolucion(any()) }
    }

    @Test
    fun `alta_articulo ejecutado construye Entrada-DeArticuloNuevo con los campos correctos`(@TempDir tempDir: File) = runTest {
        val entradaRepository = mockk<EntradaRepository>()
        coEvery { entradaRepository.registrarEntrada(any()) } returns Unit
        val ejecutor = ejecutor(entradaRepository = entradaRepository, appLogger = AppLogger(tempDir))
        val accion = AccionIaDto(
            modulo = "entrada",
            tipo = "alta_articulo",
            parametros = buildJsonObject {
                put("sku", "TOR-001")
                put("nombre", "Tornillo 1/2")
                put("unidadMedida", "pieza")
                put("cantidad", "10")
                put("precioVenta", "50.00")
                put("costo", "30.00")
            },
        )

        val resultado = ejecutor.ejecutar(accion, setOf("entrada"), "suc-1", "german")

        assertTrue(resultado is ResultadoAccionIa.Ejecutada)
        coVerify {
            entradaRepository.registrarEntrada(
                match { entrada ->
                    entrada is Entrada.DeArticuloNuevo &&
                        entrada.sucursalId == "suc-1" &&
                        entrada.usuarioId == "german" &&
                        entrada.cantidad == BigDecimal("10") &&
                        entrada.articulo.sku == "TOR-001" &&
                        entrada.articulo.nombre == "Tornillo 1/2" &&
                        entrada.articulo.precioVenta == BigDecimal("50.00") &&
                        entrada.articulo.costo == BigDecimal("30.00")
                },
            )
        }
    }

    // Hallazgo de pruebas en el Xiaomi: dictar la misma alta dos veces
    // (misma descripcion) creaba dos filas de catalogo en vez de sumar
    // cantidad a la ya existente.
    @Test
    fun `alta_articulo con un nombre ya existente en el catalogo suma cantidad en vez de duplicar`(@TempDir tempDir: File) = runTest {
        val entradaRepository = mockk<EntradaRepository>()
        coEvery { entradaRepository.registrarEntrada(any()) } returns Unit
        val articuloExistente = Articulo(
            id = "art-existente",
            sku = "COCA-2L",
            nombre = "Coca de 2L",
            unidadMedida = "pieza",
            precioVenta = BigDecimal("50.00"),
        )
        val ejecutor = ejecutor(
            entradaRepository = entradaRepository,
            inventarioRepository = inventarioRepositoryConArticulos(listOf(articuloExistente)),
            appLogger = AppLogger(tempDir),
        )
        val accion = AccionIaDto(
            modulo = "entrada",
            tipo = "alta_articulo",
            parametros = buildJsonObject {
                put("sku", "COCA-2L-OTRO")
                put("nombre", "Coca de 2L")
                put("unidadMedida", "pieza")
                put("cantidad", "5")
                put("precioVenta", "50.00")
            },
        )

        val resultado = ejecutor.ejecutar(accion, setOf("entrada"), "suc-1", "german")

        assertTrue(resultado is ResultadoAccionIa.Ejecutada)
        assertTrue((resultado as ResultadoAccionIa.Ejecutada).mensaje.contains("ya existía"))
        coVerify {
            entradaRepository.registrarEntrada(
                match { entrada ->
                    entrada is Entrada.DeArticuloExistente &&
                        entrada.articuloId == "art-existente" &&
                        entrada.cantidad == BigDecimal("5")
                },
            )
        }
    }

    // Hallazgo de code-reviewer sobre el fix anterior: el fallback por sku
    // reusaba los candidatos de la busqueda POR NOMBRE, que ya vienen
    // filtrados por "nombre LIKE %nombre_dictado%" - si el nombre dictado
    // difiere del guardado (tipico del reconocimiento de voz), el articulo
    // existente ni aparecia como candidato y el fallback quedaba
    // inalcanzable. Este test dicta un nombre DISTINTO al guardado pero con
    // el mismo sku, y confirma que igual lo encuentra (consulta separada por
    // sku, no reusa los candidatos de la busqueda por nombre).
    @Test
    fun `alta_articulo con un nombre distinto pero el mismo sku suma cantidad via el fallback por sku`(@TempDir tempDir: File) = runTest {
        val entradaRepository = mockk<EntradaRepository>()
        coEvery { entradaRepository.registrarEntrada(any()) } returns Unit
        val articuloExistente = Articulo(
            id = "art-existente",
            sku = "COCA-2L",
            nombre = "Coca de 2 litros",
            unidadMedida = "pieza",
            precioVenta = BigDecimal("50.00"),
        )
        val ejecutor = ejecutor(
            entradaRepository = entradaRepository,
            inventarioRepository = inventarioRepositoryConArticulos(listOf(articuloExistente)),
            appLogger = AppLogger(tempDir),
        )
        val accion = AccionIaDto(
            modulo = "entrada",
            tipo = "alta_articulo",
            parametros = buildJsonObject {
                put("sku", "COCA-2L")
                put("nombre", "Coca cola dos litros")
                put("unidadMedida", "pieza")
                put("cantidad", "5")
                put("precioVenta", "50.00")
            },
        )

        val resultado = ejecutor.ejecutar(accion, setOf("entrada"), "suc-1", "german")

        assertTrue(resultado is ResultadoAccionIa.Ejecutada)
        coVerify {
            entradaRepository.registrarEntrada(
                match { entrada -> entrada is Entrada.DeArticuloExistente && entrada.articuloId == "art-existente" },
            )
        }
    }

    @Test
    fun `registrar_devolucion ejecutado construye Devolucion con folio, estado y linea correctos`(@TempDir tempDir: File) = runTest {
        val devolucionRepository = mockk<DevolucionRepository>()
        coEvery { devolucionRepository.registrarDevolucion(any()) } returns Unit
        val ejecutor = ejecutor(devolucionRepository = devolucionRepository, appLogger = AppLogger(tempDir))
        val accion = AccionIaDto(
            modulo = "devoluciones",
            tipo = "registrar_devolucion",
            parametros = buildJsonObject {
                put("articuloId", "art-1")
                put("cantidad", "2")
                put("motivo", "producto danado")
                put("condicion", "defectuoso")
            },
        )

        val resultado = ejecutor.ejecutar(accion, setOf("devoluciones"), "suc-1", "german")

        assertTrue(resultado is ResultadoAccionIa.Ejecutada)
        coVerify {
            devolucionRepository.registrarDevolucion(
                match { devolucion ->
                    devolucion.sucursalId == "suc-1" &&
                        devolucion.usuarioId == "german" &&
                        devolucion.estado == "registrada" &&
                        devolucion.folio.startsWith("D-") &&
                        devolucion.lineas.size == 1 &&
                        devolucion.lineas[0].articuloId == "art-1" &&
                        devolucion.lineas[0].cantidad == BigDecimal("2") &&
                        devolucion.lineas[0].motivo == "producto danado" &&
                        devolucion.lineas[0].condicion == "defectuoso"
                },
            )
        }
    }
}
