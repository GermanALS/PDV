package com.pdv.pos.ia

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.caja.CajaRefreshSignal
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.ModeAwareCajaRepository
import com.pdv.pos.data.ModeAwareEntradaRepository
import com.pdv.pos.data.local.CajaDao
import com.pdv.pos.data.local.EntradaDao
import com.pdv.pos.data.local.LocalCajaRepository
import com.pdv.pos.data.local.LocalEntradaRepository
import com.pdv.pos.data.local.RetiroDao
import com.pdv.pos.data.local.VentaDao
import com.pdv.pos.data.remote.RemoteCajaRepository
import com.pdv.pos.data.remote.RemoteEntradaRepository
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
import com.pdv.pos.inventario.BuscadorArticuloExistente
import com.pdv.pos.inventario.InventarioRefreshSignal
import com.pdv.pos.inventario.export.ArchivoExportado
import com.pdv.pos.inventario.export.InventarioExportManager
import com.pdv.pos.logging.AppLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
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

    // Repositorio de stock con categorias reales y busqueda LIKE sobre
    // nombre/sku/codigoBarras (mismo contrato que
    // inventarioRepositoryConArticulos, pero con cantidad e InventarioItem
    // completos - las pruebas de exportar_inventario/consultar_stock
    // necesitan sumar/filtrar por categoria real, no solo confirmar
    // coincidencia).
    private fun inventarioRepositoryDeStock(
        items: List<InventarioItem>,
        categorias: List<String> = items.mapNotNull { it.articulo.categoria }.distinct(),
    ): InventarioRepository {
        val repo = mockk<InventarioRepository>()
        every { repo.observarCategorias() } returns flowOf(categorias)
        every { repo.observarInventario(any(), any(), any(), any()) } answers {
            val termino = secondArg<String>()
            val filtrados = if (termino.isBlank()) {
                items
            } else {
                items.filter {
                    it.articulo.nombre.contains(termino, ignoreCase = true) ||
                        it.articulo.sku.contains(termino, ignoreCase = true) ||
                        (it.articulo.codigoBarras?.contains(termino, ignoreCase = true) == true)
                }
            }
            flowOf(PaginaInventario(items = filtrados, pagina = 1, tamanioPagina = 100, total = filtrados.size))
        }
        return repo
    }

    private fun ejecutor(
        entradaRepository: EntradaRepository = mockk(relaxed = true),
        cajaRepository: CajaRepository = mockk(relaxed = true),
        retiroEfectivoRepository: RetiroEfectivoRepository = mockk(relaxed = true),
        devolucionRepository: DevolucionRepository = mockk(relaxed = true),
        inventarioRepository: InventarioRepository = inventarioRepositorySinCoincidencias(),
        inventarioExportManager: InventarioExportManager = mockk(relaxed = true),
        appLogger: AppLogger,
        faqRepository: FaqRepository = mockk(relaxed = true),
    ) = EjecutorAccionesIa(
        entradaRepository,
        cajaRepository,
        retiroEfectivoRepository,
        devolucionRepository,
        BuscadorArticuloExistente(inventarioRepository),
        inventarioRepository,
        inventarioExportManager,
        appLogger,
        json,
        faqRepository,
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

    // PLAN.md Parte 18, sub-parte D: exportar_inventario/consultar_stock son
    // de solo lectura (nunca requieren tarjeta de confirmacion, ver
    // ChatViewModel) pero igual pasan por el mismo chequeo de permiso que el
    // resto de las acciones, contra el modulo "inventario".
    @Test
    fun `exportar_inventario sin filtro exporta el catalogo completo y devuelve el archivo para compartir`(
        @TempDir tempDir: File,
    ) = runTest {
        val coca = InventarioItem(
            Articulo(id = "a1", sku = "SKU-1", nombre = "Coca 2L", unidadMedida = "pieza", precioVenta = BigDecimal("50.00"), categoria = "Bebidas"),
            BigDecimal("10"),
            null,
        )
        val papas = InventarioItem(
            Articulo(id = "a2", sku = "SKU-2", nombre = "Papas fritas", unidadMedida = "pieza", precioVenta = BigDecimal("20.00"), categoria = "Snacks"),
            BigDecimal("5"),
            null,
        )
        val inventarioRepository = inventarioRepositoryDeStock(listOf(coca, papas))
        val exportManager = mockk<InventarioExportManager>()
        val archivo = ArchivoExportado(mockk(relaxed = true), "text/csv")
        coEvery { exportManager.exportarCsv(listOf(coca, papas)) } returns archivo

        val ejecutor = ejecutor(
            inventarioRepository = inventarioRepository,
            inventarioExportManager = exportManager,
            appLogger = AppLogger(tempDir),
        )
        val accion = AccionIaDto(modulo = "inventario", tipo = "exportar_inventario", parametros = buildJsonObject { })

        val resultado = ejecutor.ejecutar(accion, setOf("inventario"), "suc-1", "german")

        assertTrue(resultado is ResultadoAccionIa.Ejecutada)
        assertEquals(archivo, (resultado as ResultadoAccionIa.Ejecutada).archivoParaCompartir)
        coVerify { exportManager.exportarCsv(listOf(coca, papas)) }
    }

    @Test
    fun `exportar_inventario con filtro que coincide con una categoria exporta solo esa categoria`(
        @TempDir tempDir: File,
    ) = runTest {
        val coca = InventarioItem(
            Articulo(id = "a1", sku = "SKU-1", nombre = "Coca 2L", unidadMedida = "pieza", precioVenta = BigDecimal("50.00"), categoria = "Bebidas"),
            BigDecimal("10"),
            null,
        )
        val papas = InventarioItem(
            Articulo(id = "a2", sku = "SKU-2", nombre = "Papas fritas", unidadMedida = "pieza", precioVenta = BigDecimal("20.00"), categoria = "Snacks"),
            BigDecimal("5"),
            null,
        )
        val inventarioRepository = inventarioRepositoryDeStock(listOf(coca, papas))
        val exportManager = mockk<InventarioExportManager>()
        val archivo = ArchivoExportado(mockk(relaxed = true), "text/csv")
        coEvery { exportManager.exportarCsv(listOf(coca)) } returns archivo

        val ejecutor = ejecutor(
            inventarioRepository = inventarioRepository,
            inventarioExportManager = exportManager,
            appLogger = AppLogger(tempDir),
        )
        val accion = AccionIaDto(
            modulo = "inventario",
            tipo = "exportar_inventario",
            parametros = buildJsonObject { put("filtro", "Bebidas") },
        )

        val resultado = ejecutor.ejecutar(accion, setOf("inventario"), "suc-1", "german")

        assertTrue(resultado is ResultadoAccionIa.Ejecutada)
        coVerify { exportManager.exportarCsv(listOf(coca)) }
    }

    @Test
    fun `exportar_inventario con filtro que no coincide con ninguna categoria lo trata como texto de busqueda`(
        @TempDir tempDir: File,
    ) = runTest {
        val coca = InventarioItem(
            Articulo(id = "a1", sku = "SKU-1", nombre = "Coca 2L", unidadMedida = "pieza", precioVenta = BigDecimal("50.00"), categoria = "Bebidas"),
            BigDecimal("10"),
            null,
        )
        val papas = InventarioItem(
            Articulo(id = "a2", sku = "SKU-2", nombre = "Papas fritas", unidadMedida = "pieza", precioVenta = BigDecimal("20.00"), categoria = "Snacks"),
            BigDecimal("5"),
            null,
        )
        val inventarioRepository = inventarioRepositoryDeStock(listOf(coca, papas))
        val exportManager = mockk<InventarioExportManager>()
        val archivo = ArchivoExportado(mockk(relaxed = true), "text/csv")
        coEvery { exportManager.exportarCsv(listOf(coca)) } returns archivo

        val ejecutor = ejecutor(
            inventarioRepository = inventarioRepository,
            inventarioExportManager = exportManager,
            appLogger = AppLogger(tempDir),
        )
        val accion = AccionIaDto(
            modulo = "inventario",
            tipo = "exportar_inventario",
            parametros = buildJsonObject { put("filtro", "Coca") },
        )

        val resultado = ejecutor.ejecutar(accion, setOf("inventario"), "suc-1", "german")

        assertTrue(resultado is ResultadoAccionIa.Ejecutada)
        coVerify { exportManager.exportarCsv(listOf(coca)) }
    }

    @Test
    fun `exportar_inventario sin permiso de inventario se rechaza sin exportar nada`(@TempDir tempDir: File) = runTest {
        val exportManager = mockk<InventarioExportManager>()
        val ejecutor = ejecutor(inventarioExportManager = exportManager, appLogger = AppLogger(tempDir))
        val accion = AccionIaDto(modulo = "inventario", tipo = "exportar_inventario", parametros = buildJsonObject { })

        val resultado = ejecutor.ejecutar(accion, modulosPermitidos = emptySet(), "suc-1", "german")

        assertTrue(resultado is ResultadoAccionIa.RechazadaPorPermiso)
        coVerify(exactly = 0) { exportManager.exportarCsv(any()) }
    }

    @Test
    fun `consultar_stock sin parametros devuelve el total general de todo el inventario`(@TempDir tempDir: File) = runTest {
        val items = listOf(
            InventarioItem(
                Articulo(id = "a1", sku = "SKU-1", nombre = "Coca 2L", unidadMedida = "pieza", precioVenta = BigDecimal("50.00"), categoria = "Bebidas"),
                BigDecimal("10"),
                null,
            ),
            InventarioItem(
                Articulo(id = "a2", sku = "SKU-2", nombre = "Papas fritas", unidadMedida = "pieza", precioVenta = BigDecimal("20.00"), categoria = "Snacks"),
                BigDecimal("5"),
                null,
            ),
        )
        val ejecutor = ejecutor(inventarioRepository = inventarioRepositoryDeStock(items), appLogger = AppLogger(tempDir))
        val accion = AccionIaDto(modulo = "inventario", tipo = "consultar_stock", parametros = buildJsonObject { })

        val resultado = ejecutor.ejecutar(accion, setOf("inventario"), "suc-1", "german")

        assertTrue(resultado is ResultadoAccionIa.Ejecutada)
        assertTrue((resultado as ResultadoAccionIa.Ejecutada).mensaje.contains("15"))
    }

    @Test
    fun `consultar_stock con articulo especifico suma solo las coincidencias de la busqueda`(@TempDir tempDir: File) = runTest {
        val items = listOf(
            InventarioItem(
                Articulo(id = "a1", sku = "SKU-1", nombre = "Coca 2L", unidadMedida = "pieza", precioVenta = BigDecimal("50.00"), categoria = "Bebidas"),
                BigDecimal("10"),
                null,
            ),
            InventarioItem(
                Articulo(id = "a2", sku = "SKU-2", nombre = "Coca 600ml", unidadMedida = "pieza", precioVenta = BigDecimal("20.00"), categoria = "Bebidas"),
                BigDecimal("7"),
                null,
            ),
            InventarioItem(
                Articulo(id = "a3", sku = "SKU-3", nombre = "Papas fritas", unidadMedida = "pieza", precioVenta = BigDecimal("20.00"), categoria = "Snacks"),
                BigDecimal("5"),
                null,
            ),
        )
        val ejecutor = ejecutor(inventarioRepository = inventarioRepositoryDeStock(items), appLogger = AppLogger(tempDir))
        val accion = AccionIaDto(
            modulo = "inventario",
            tipo = "consultar_stock",
            parametros = buildJsonObject { put("articulo", "Coca") },
        )

        val resultado = ejecutor.ejecutar(accion, setOf("inventario"), "suc-1", "german")

        assertTrue(resultado is ResultadoAccionIa.Ejecutada)
        assertTrue((resultado as ResultadoAccionIa.Ejecutada).mensaje.contains("17"))
    }

    @Test
    fun `consultar_stock con categoria suma solo los articulos de esa categoria`(@TempDir tempDir: File) = runTest {
        val items = listOf(
            InventarioItem(
                Articulo(id = "a1", sku = "SKU-1", nombre = "Coca 2L", unidadMedida = "pieza", precioVenta = BigDecimal("50.00"), categoria = "Bebidas"),
                BigDecimal("10"),
                null,
            ),
            InventarioItem(
                Articulo(id = "a2", sku = "SKU-2", nombre = "Agua", unidadMedida = "pieza", precioVenta = BigDecimal("15.00"), categoria = "Bebidas"),
                BigDecimal("3"),
                null,
            ),
            InventarioItem(
                Articulo(id = "a3", sku = "SKU-3", nombre = "Papas fritas", unidadMedida = "pieza", precioVenta = BigDecimal("20.00"), categoria = "Snacks"),
                BigDecimal("5"),
                null,
            ),
        )
        val ejecutor = ejecutor(inventarioRepository = inventarioRepositoryDeStock(items), appLogger = AppLogger(tempDir))
        val accion = AccionIaDto(
            modulo = "inventario",
            tipo = "consultar_stock",
            parametros = buildJsonObject { put("categoria", "Bebidas") },
        )

        val resultado = ejecutor.ejecutar(accion, setOf("inventario"), "suc-1", "german")

        assertTrue(resultado is ResultadoAccionIa.Ejecutada)
        assertTrue((resultado as ResultadoAccionIa.Ejecutada).mensaje.contains("13"))
    }

    @Test
    fun `consultar_stock sin permiso de inventario se rechaza`(@TempDir tempDir: File) = runTest {
        val ejecutor = ejecutor(appLogger = AppLogger(tempDir))
        val accion = AccionIaDto(modulo = "inventario", tipo = "consultar_stock", parametros = buildJsonObject { })

        val resultado = ejecutor.ejecutar(accion, modulosPermitidos = emptySet(), "suc-1", "german")

        assertTrue(resultado is ResultadoAccionIa.RechazadaPorPermiso)
    }

    // PLAN.md Parte 20, sub-paso 3: consultar_faq es de solo lectura (modulo
    // "ia", que el widget ya exige) y devuelve el "answer" de la entrada tal
    // cual, con sus marcadores de formato originales - sin parafraseo.
    @Test
    fun `consultar_faq devuelve el texto de la entrada del FAQ tal cual`(@TempDir tempDir: File) = runTest {
        val faqRepository = mockk<FaqRepository>()
        every { faqRepository.find(5) } returns
            FaqEntry(5, "Como cobro en efectivo?", "**Efectivo**: la app pide el monto recibido y calcula el cambio.")
        val ejecutor = ejecutor(faqRepository = faqRepository, appLogger = AppLogger(tempDir))
        val accion = AccionIaDto(modulo = "ia", tipo = "consultar_faq", parametros = buildJsonObject { put("numero", "5") })

        val resultado = ejecutor.ejecutar(accion, setOf("ia"), "suc-1", "german")

        assertTrue(resultado is ResultadoAccionIa.Ejecutada)
        assertEquals(
            "**Efectivo**: la app pide el monto recibido y calcula el cambio.",
            (resultado as ResultadoAccionIa.Ejecutada).mensaje,
        )
    }

    @Test
    fun `consultar_faq con un numero inexistente falla sin crashear`(@TempDir tempDir: File) = runTest {
        val faqRepository = mockk<FaqRepository>()
        every { faqRepository.find(any()) } returns null
        val ejecutor = ejecutor(faqRepository = faqRepository, appLogger = AppLogger(tempDir))
        val accion = AccionIaDto(modulo = "ia", tipo = "consultar_faq", parametros = buildJsonObject { put("numero", "999") })

        val resultado = ejecutor.ejecutar(accion, setOf("ia"), "suc-1", "german")

        assertTrue(resultado is ResultadoAccionIa.Fallida)
    }

    // PLAN.md Parte 18, sub-parte E: alta_articulo llama a
    // EntradaRepository.registrarEntrada igual que la pantalla manual de
    // Entrada - con un ModeAwareEntradaRepository real (en vez del mock
    // relajado del resto de este archivo), confirma que ese mismo camino
    // tambien dispara InventarioRefreshSignal, sin necesitar una emision
    // aparte dentro de EjecutorAccionesIa.
    @Test
    fun `alta_articulo via la IA tambien dispara la señal de refresco de inventario`(@TempDir tempDir: File) = runTest {
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { File(tempDir, "test.preferences_pb") })
        val preferences = ConfiguracionPreferences(dataStore)
        val appLogger = AppLogger(tempDir)
        val dao = mockk<EntradaDao>()
        coEvery { dao.insertEntradaCompleta(any(), any(), any(), any(), any(), any(), any()) } returns Unit
        val local = LocalEntradaRepository(dao, appLogger)
        val remote = mockk<RemoteEntradaRepository>(relaxed = true)
        val signal = InventarioRefreshSignal()
        val entradaRepository = ModeAwareEntradaRepository(
            local = local,
            remote = remote,
            preferences = preferences,
            inventarioRefreshSignal = signal,
        )
        val ejecutor = ejecutor(entradaRepository = entradaRepository, appLogger = appLogger)
        val accion = AccionIaDto(
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

        val resultado = ejecutor.ejecutar(accion, setOf("entrada"), "suc-1", "german")

        assertTrue(resultado is ResultadoAccionIa.Ejecutada)
        withTimeout(1000) { signal.refrescos.first() }
    }

    // PLAN.md Parte 18, sub-parte F: corte_parcial llama a
    // CajaRepository.guardarCorte igual que la pantalla manual de Caja -
    // con un ModeAwareCajaRepository real, confirma que ese mismo camino
    // dispara CajaRefreshSignal (mismo criterio que la prueba de
    // alta_articulo/InventarioRefreshSignal de la sub-parte E).
    @Test
    fun `corte_parcial via la IA tambien dispara la señal de refresco de caja`(@TempDir tempDir: File) = runTest {
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { File(tempDir, "test.preferences_pb") })
        val preferences = ConfiguracionPreferences(dataStore)
        val appLogger = AppLogger(tempDir)
        val cajaDao = mockk<CajaDao>()
        coEvery { cajaDao.insertCorte(any()) } returns Unit
        val ventaDao = mockk<VentaDao>()
        coEvery { ventaDao.getVentasDelPeriodo(any(), any(), any()) } returns emptyList()
        val retiroDao = mockk<RetiroDao>()
        coEvery { retiroDao.getRetirosDelPeriodo(any(), any(), any()) } returns emptyList()
        val local = LocalCajaRepository(cajaDao, ventaDao, retiroDao, appLogger)
        val remote = mockk<RemoteCajaRepository>(relaxed = true)
        val signal = CajaRefreshSignal()
        val cajaRepository = ModeAwareCajaRepository(
            local = local,
            remote = remote,
            preferences = preferences,
            cajaRefreshSignal = signal,
        )
        val ejecutor = ejecutor(cajaRepository = cajaRepository, appLogger = appLogger)
        val accion = AccionIaDto(modulo = "caja", tipo = "corte_parcial", parametros = buildJsonObject { })

        val resultado = ejecutor.ejecutar(accion, setOf("caja"), "suc-1", "german")

        assertTrue(resultado is ResultadoAccionIa.Ejecutada)
        withTimeout(1000) { signal.refrescos.first() }
    }
}
