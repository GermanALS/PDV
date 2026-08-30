package com.pdv.pos.ia

import com.pdv.pos.auth.Session
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.config.DeviceConfig
import com.pdv.pos.config.IaConfig
import com.pdv.pos.config.IaPreferences
import com.pdv.pos.config.PromptIaPreferences
import com.pdv.pos.data.remote.ApiResult
import com.pdv.pos.data.remote.dto.ChatMessageDto
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.Rol
import com.pdv.pos.domain.repository.RolRepository
import com.pdv.pos.inventario.export.ArchivoExportado
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

// PLAN.md Parte 16, sub-paso 1: verifica el widget de chat a nivel de
// ViewModel (mismo criterio ya establecido en el proyecto para "Compose UI
// Test" - CLAUDE.md sec. 6 pide testear el ViewModel, no detalles de
// implementacion de Compose; la confirmacion visual real queda para el
// needs-device del sub-paso 5).
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val rolConIa = Rol(id = "r1", nombre = "Encargado", modulosPermitidos = listOf("ia", "entrada"))
    private val rolSinIa = Rol(id = "r2", nombre = "Cajero", modulosPermitidos = listOf("entrada"))
    private val sesion = Session(username = "german", usuarioId = "u1", rolId = "r1")

    private fun sessionManager(session: Session? = sesion): SessionManager {
        val manager = mockk<SessionManager>(relaxed = true)
        every { manager.session } returns MutableStateFlow(session).asStateFlow()
        return manager
    }

    private fun rolRepository(roles: List<Rol> = listOf(rolConIa, rolSinIa)): RolRepository {
        val repository = mockk<RolRepository>()
        every { repository.observeRoles() } returns flowOf(roles)
        return repository
    }

    private fun preferences(sucursalId: String? = "suc-1"): ConfiguracionPreferences {
        val preferences = mockk<ConfiguracionPreferences>()
        every { preferences.deviceConfig } returns flowOf(
            DeviceConfig(backendMode = BackendMode.LOCAL, ip = "", puerto = "", sucursalIdSeleccionada = sucursalId),
        )
        return preferences
    }

    private fun iaPreferences(activo: Boolean = true, token: String? = "token-1"): IaPreferences {
        val preferences = mockk<IaPreferences>()
        every { preferences.config } returns flowOf(
            IaConfig(activo = activo, proveedor = LlmProvider.DEEP_SEEK, modelo = "deepseek-chat", tieneToken = token != null),
        )
        coEvery { preferences.getToken() } returns token
        return preferences
    }

    private fun promptIaPreferences(): PromptIaPreferences {
        val preferences = mockk<PromptIaPreferences>()
        every { preferences.prompt } returns flowOf(PROMPT_SISTEMA_DEFAULT)
        return preferences
    }

    private fun estadoPuntoVentaBuilder(): EstadoPuntoVentaBuilder {
        val builder = mockk<EstadoPuntoVentaBuilder>()
        coEvery { builder.armarJson(any(), any()) } returns "{}"
        return builder
    }

    private fun faqRepository(): FaqRepository {
        val faq = mockk<FaqRepository>()
        every { faq.textoAyuda } returns "FAQ de prueba"
        every { faq.instruccionFaq } returns "# FAQ\n1. Como cobro en efectivo?"
        every { faq.find(any()) } returns null
        every { faq.matchPorTexto(any()) } returns null
        return faq
    }

    private fun viewModel(
        sessionManager: SessionManager = sessionManager(),
        rolRepository: RolRepository = rolRepository(),
        preferences: ConfiguracionPreferences = preferences(),
        iaPreferences: IaPreferences = iaPreferences(),
        llmClient: LlmClient = mockk(),
        ejecutorAccionesIa: EjecutorAccionesIa = mockk(),
        faqRepository: FaqRepository = faqRepository(),
    ) = ChatViewModel(
        sessionManager,
        rolRepository,
        preferences,
        iaPreferences,
        promptIaPreferences(),
        estadoPuntoVentaBuilder(),
        llmClient,
        ejecutorAccionesIa,
        faqRepository,
    )

    @Test
    fun `el widget es visible solo si el usuario en turno tiene el modulo ia`() = runTest(dispatcher) {
        val viewModel = viewModel(sessionManager = sessionManager(sesion.copy(rolId = "r1")))

        assertTrue(viewModel.uiState.value.visible)
    }

    @Test
    fun `el widget queda oculto si el usuario en turno no tiene el modulo ia`() = runTest(dispatcher) {
        val viewModel = viewModel(sessionManager = sessionManager(sesion.copy(rolId = "r2")))

        assertEquals(false, viewModel.uiState.value.visible)
    }

    // PLAN.md Parte 20, sub-paso 6: el panel de Ayuda y el fallback sin
    // conexion muestran faqTexto, que ahora se arma desde FaqRepository
    // (reemplaza al Markdown libre de res/raw/faq.md, eliminado).
    @Test
    fun `faqTexto viene del FaqRepository`() = runTest(dispatcher) {
        val faq = mockk<FaqRepository>()
        every { faq.textoAyuda } returns "P: Como cobro?\nR: Con efectivo o tarjeta."
        every { faq.instruccionFaq } returns "# FAQ"
        every { faq.find(any()) } returns null
        val viewModel = viewModel(faqRepository = faq)

        assertEquals("P: Como cobro?\nR: Con efectivo o tarjeta.", viewModel.faqTexto)
    }

    // PLAN.md Parte 16, sub-paso 2: la voz es solo otra forma de llenar el
    // mismo campo de texto del chat (VoiceInputButton.onTranscripcion se
    // cablea directo a este mismo metodo, ver AsistenteIaWidget.kt) - no hay
    // estado ni logica separada para el resultado de una transcripcion.
    @Test
    fun `una transcripcion de voz llena el campo de texto del chat, igual que escribir`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onTextoChange("agregá 10 tornillos a 5 pesos")

        assertEquals("agregá 10 tornillos a 5 pesos", viewModel.uiState.value.entradaTexto)
    }

    @Test
    fun `enviar un mensaje agrega la respuesta de la IA y una tarjeta de accion pendiente`() = runTest(dispatcher) {
        val llmClient = mockk<LlmClient>()
        val accion = AccionIaDto(modulo = "caja", tipo = "corte_parcial", parametros = buildJsonObject { })
        coEvery { llmClient.chatEstructurado(any(), any(), any(), any()) } returns
            ApiResult.Success(RespuestaIaDto(respuestaUsuario = "Listo, hago el corte.", acciones = listOf(accion)))
        val viewModel = viewModel(llmClient = llmClient)

        viewModel.onTextoChange("hace un corte parcial")
        viewModel.enviarMensaje()

        val mensajes = viewModel.uiState.value.mensajes
        assertTrue(mensajes.any { it is ChatUiMessage.DeUsuario && it.texto == "hace un corte parcial" })
        assertTrue(mensajes.any { it is ChatUiMessage.DeIa && it.texto == "Listo, hago el corte." })
        assertTrue(mensajes.any { it is ChatUiMessage.AccionPendiente && !it.resuelta })
        assertEquals(false, viewModel.uiState.value.enviando)
        assertEquals("", viewModel.uiState.value.entradaTexto)
    }

    // Hallazgo de pruebas en el Xiaomi (sub-paso 5): el prompt aprobado
    // (Parte 15) describe las acciones en prosa pero nunca especifica el
    // JSON exacto que RespuestaIaDto/AccionIaDto esperan - sin
    // FORMATO_SALIDA_ACCIONES, cualquier pedido de accion fallaba con "La
    // IA devolvio una respuesta con formato invalido" (SerializationException
    // en LlmClient). Se verifica que el mensaje de sistema real enviado al
    // proveedor incluye las claves exactas de cada tipo de accion.
    @Test
    fun `el mensaje de sistema incluye el formato exacto de las acciones, no solo la descripcion en prosa`() = runTest(dispatcher) {
        val llmClient = mockk<LlmClient>()
        val mensajesCapturados = slot<List<ChatMessageDto>>()
        coEvery { llmClient.chatEstructurado(any(), any(), any(), capture(mensajesCapturados)) } returns
            ApiResult.Success(RespuestaIaDto(respuestaUsuario = "Listo.", acciones = emptyList()))
        val viewModel = viewModel(llmClient = llmClient)

        viewModel.onTextoChange("hola")
        viewModel.enviarMensaje()

        val mensajeSistema = mensajesCapturados.captured.single { it.role == "system" }
        assertTrue(mensajeSistema.content.contains(PROMPT_SISTEMA_DEFAULT))
        assertTrue(mensajeSistema.content.contains("alta_articulo"))
        assertTrue(mensajeSistema.content.contains("precioVenta"))
        assertTrue(mensajeSistema.content.contains("registrar_devolucion"))
    }

    // PLAN.md Parte 20, sub-paso 2: la lista de preguntas del FAQ curado
    // (FaqRepository.instruccionFaq) se concatena al prompt de sistema en
    // runtime, despues de FORMATO_SALIDA_ACCIONES, para que el modelo
    // responda via la consulta de solo lectura "consultar_faq".
    @Test
    fun `el mensaje de sistema incluye la instruccion de FAQ con la lista de preguntas`() = runTest(dispatcher) {
        val llmClient = mockk<LlmClient>()
        val mensajesCapturados = slot<List<ChatMessageDto>>()
        coEvery { llmClient.chatEstructurado(any(), any(), any(), capture(mensajesCapturados)) } returns
            ApiResult.Success(RespuestaIaDto(respuestaUsuario = "Listo.", acciones = emptyList()))
        val viewModel = viewModel(llmClient = llmClient)

        viewModel.onTextoChange("hola")
        viewModel.enviarMensaje()

        val mensajeSistema = mensajesCapturados.captured.single { it.role == "system" }
        assertTrue(mensajeSistema.content.contains("# FAQ"))
        assertTrue(mensajeSistema.content.contains("Como cobro en efectivo?"))
    }

    @Test
    fun `enviar un mensaje sin sucursal seleccionada muestra un error sin llamar al proveedor`() = runTest(dispatcher) {
        val llmClient = mockk<LlmClient>()
        val viewModel = viewModel(preferences = preferences(sucursalId = null), llmClient = llmClient)

        viewModel.onTextoChange("hola")
        viewModel.enviarMensaje()

        assertTrue(viewModel.uiState.value.mensajes.any { it is ChatUiMessage.DeError })
        coVerify(exactly = 0) { llmClient.chatEstructurado(any(), any(), any(), any()) }
    }

    @Test
    fun `enviar un mensaje con la IA desactivada muestra un error sin llamar al proveedor`() = runTest(dispatcher) {
        val llmClient = mockk<LlmClient>()
        val viewModel = viewModel(iaPreferences = iaPreferences(activo = false), llmClient = llmClient)

        viewModel.onTextoChange("hola")
        viewModel.enviarMensaje()

        assertTrue(viewModel.uiState.value.mensajes.any { it is ChatUiMessage.DeError })
        coVerify(exactly = 0) { llmClient.chatEstructurado(any(), any(), any(), any()) }
    }

    @Test
    fun `confirmar una accion la ejecuta con los permisos del rol y marca el resultado`() = runTest(dispatcher) {
        val llmClient = mockk<LlmClient>()
        val accion = AccionIaDto(modulo = "caja", tipo = "corte_parcial", parametros = buildJsonObject { })
        coEvery { llmClient.chatEstructurado(any(), any(), any(), any()) } returns
            ApiResult.Success(RespuestaIaDto(respuestaUsuario = "Listo.", acciones = listOf(accion)))
        val ejecutor = mockk<EjecutorAccionesIa>()
        coEvery { ejecutor.ejecutar(any(), any(), any(), any()) } returns ResultadoAccionIa.Ejecutada("Corte parcial registrado.")
        val viewModel = viewModel(llmClient = llmClient, ejecutorAccionesIa = ejecutor)
        viewModel.onTextoChange("hace un corte parcial")
        viewModel.enviarMensaje()
        val pendiente = viewModel.uiState.value.mensajes.filterIsInstance<ChatUiMessage.AccionPendiente>().single()

        viewModel.confirmarAccion(pendiente.id)

        coVerify { ejecutor.ejecutar(accion, setOf("ia", "entrada"), "suc-1", "german") }
        val resuelta = viewModel.uiState.value.mensajes.filterIsInstance<ChatUiMessage.AccionPendiente>().single()
        assertTrue(resuelta.resuelta)
        assertEquals("Corte parcial registrado.", resuelta.mensajeResultado)
    }

    // PLAN.md Parte 18, sub-parte D: a diferencia de las 4 acciones de
    // escritura (siempre tarjeta AccionPendiente), las dos consultas de solo
    // lectura se ejecutan de inmediato al llegar la respuesta de la IA.
    @Test
    fun `una consulta de solo lectura se ejecuta de inmediato sin tarjeta de confirmacion`() = runTest(dispatcher) {
        val llmClient = mockk<LlmClient>()
        val accion = AccionIaDto(modulo = "inventario", tipo = "consultar_stock", parametros = buildJsonObject { })
        coEvery { llmClient.chatEstructurado(any(), any(), any(), any()) } returns
            ApiResult.Success(RespuestaIaDto(respuestaUsuario = "Consultando...", acciones = listOf(accion)))
        val ejecutor = mockk<EjecutorAccionesIa>()
        coEvery { ejecutor.ejecutar(any(), any(), any(), any()) } returns
            ResultadoAccionIa.Ejecutada("El stock de el inventario total es 42.")
        val viewModel = viewModel(llmClient = llmClient, ejecutorAccionesIa = ejecutor)

        viewModel.onTextoChange("cuanto stock total hay")
        viewModel.enviarMensaje()

        coVerify { ejecutor.ejecutar(accion, setOf("ia", "entrada"), "suc-1", "german") }
        val mensajes = viewModel.uiState.value.mensajes
        assertTrue(mensajes.none { it is ChatUiMessage.AccionPendiente })
        assertTrue(mensajes.any { it is ChatUiMessage.DeIa && it.texto == "El stock de el inventario total es 42." })
    }

    // PLAN.md Parte 20, sub-paso 3: consultar_faq entra por el mismo camino
    // de solo lectura que consultar_stock - se ejecuta al instante (sin
    // tarjeta AccionPendiente) y el "answer" verbatim aparece como mensaje
    // de IA, ademas de la frase introductoria del modelo.
    @Test
    fun `consultar_faq se ejecuta de inmediato y su respuesta aparece como mensaje de IA`() = runTest(dispatcher) {
        val llmClient = mockk<LlmClient>()
        val accion = AccionIaDto(modulo = "ia", tipo = "consultar_faq", parametros = buildJsonObject { put("numero", "3") })
        coEvery { llmClient.chatEstructurado(any(), any(), any(), any()) } returns
            ApiResult.Success(RespuestaIaDto(respuestaUsuario = "Te muestro la respuesta:", acciones = listOf(accion)))
        val ejecutor = mockk<EjecutorAccionesIa>()
        coEvery { ejecutor.ejecutar(any(), any(), any(), any()) } returns
            ResultadoAccionIa.Ejecutada("**Efectivo**: la app pide el monto recibido y calcula el cambio.")
        val viewModel = viewModel(llmClient = llmClient, ejecutorAccionesIa = ejecutor)

        viewModel.onTextoChange("como cobro en efectivo?")
        viewModel.enviarMensaje()

        coVerify { ejecutor.ejecutar(accion, setOf("ia", "entrada"), "suc-1", "german") }
        val mensajes = viewModel.uiState.value.mensajes
        assertTrue(mensajes.none { it is ChatUiMessage.AccionPendiente })
        assertTrue(mensajes.any { it is ChatUiMessage.DeIa && it.texto.contains("calcula el cambio") })
    }

    // PLAN.md Parte 20, sub-paso 4: el atajo "qN" responde una entrada del
    // FAQ sin llamar al proveedor - no requiere token, sucursal ni conexion.
    @Test
    fun `el atajo qN responde la entrada del FAQ sin llamar al proveedor`() = runTest(dispatcher) {
        val llmClient = mockk<LlmClient>()
        val faq = mockk<FaqRepository>()
        every { faq.textoAyuda } returns "FAQ de prueba"
        every { faq.instruccionFaq } returns "# FAQ"
        every { faq.find(3) } returns
            FaqEntry(3, "Como cobro en efectivo?", "**Efectivo**: la app calcula el cambio.")
        val viewModel = viewModel(llmClient = llmClient, faqRepository = faq)

        viewModel.onTextoChange("q3")
        viewModel.enviarMensaje()

        coVerify(exactly = 0) { llmClient.chatEstructurado(any(), any(), any(), any()) }
        val mensajes = viewModel.uiState.value.mensajes
        assertTrue(mensajes.any { it is ChatUiMessage.DeUsuario && it.texto == "q3" })
        assertTrue(mensajes.any { it is ChatUiMessage.DeIa && it.texto == "**Efectivo**: la app calcula el cambio." })
        assertEquals("", viewModel.uiState.value.entradaTexto)
        assertEquals(false, viewModel.uiState.value.enviando)
    }

    @Test
    fun `el atajo qN con un numero inexistente responde que no encontro la pregunta`() = runTest(dispatcher) {
        val faq = mockk<FaqRepository>()
        every { faq.textoAyuda } returns "x"
        every { faq.instruccionFaq } returns "# FAQ"
        every { faq.find(any()) } returns null
        val viewModel = viewModel(faqRepository = faq)

        viewModel.onTextoChange("q99")
        viewModel.enviarMensaje()

        assertTrue(
            viewModel.uiState.value.mensajes.any {
                it is ChatUiMessage.DeIa && it.texto.contains("No encontré la pregunta 99")
            },
        )
    }

    @Test
    fun `un mensaje que no es el atajo qN sigue yendo al proveedor`() = runTest(dispatcher) {
        val llmClient = mockk<LlmClient>()
        coEvery { llmClient.chatEstructurado(any(), any(), any(), any()) } returns
            ApiResult.Success(RespuestaIaDto(respuestaUsuario = "Hola.", acciones = emptyList()))
        val viewModel = viewModel(llmClient = llmClient)

        viewModel.onTextoChange("que hago con la pregunta q3")
        viewModel.enviarMensaje()

        coVerify(exactly = 1) { llmClient.chatEstructurado(any(), any(), any(), any()) }
    }

    // PLAN.md Parte 20 (ronda de dispositivo 2026-08-30): una pregunta que
    // coincide con una del FAQ salvo por "¿"/acentos/mayusculas se responde
    // sin llamar al proveedor (cero tokens), igual que el atajo "qN".
    @Test
    fun `una pregunta que coincide con el FAQ salvo signos y acentos se responde sin llamar al proveedor`() = runTest(dispatcher) {
        val llmClient = mockk<LlmClient>()
        val faq = mockk<FaqRepository>()
        every { faq.textoAyuda } returns "x"
        every { faq.instruccionFaq } returns "# FAQ"
        every { faq.matchPorTexto("¿Cómo cobro en efectivo?") } returns
            FaqEntry(5, "Como cobro en efectivo?", "**Efectivo**: la app calcula el cambio.")
        every { faq.find(5) } returns
            FaqEntry(5, "Como cobro en efectivo?", "**Efectivo**: la app calcula el cambio.")
        val viewModel = viewModel(llmClient = llmClient, faqRepository = faq)

        viewModel.onTextoChange("¿Cómo cobro en efectivo?")
        viewModel.enviarMensaje()

        coVerify(exactly = 0) { llmClient.chatEstructurado(any(), any(), any(), any()) }
        assertTrue(
            viewModel.uiState.value.mensajes.any {
                it is ChatUiMessage.DeIa && it.texto == "**Efectivo**: la app calcula el cambio."
            },
        )
    }

    // PLAN.md Parte 20 (ronda de dispositivo 2026-08-30): si la respuesta
    // del modelo trae consultar_faq como unica accion, no se muestra su
    // respuesta_usuario improvisada - solo el texto verbatim del FAQ.
    @Test
    fun `cuando la IA responde con consultar_faq no se muestra su respuesta_usuario improvisada`() = runTest(dispatcher) {
        val llmClient = mockk<LlmClient>()
        val accion = AccionIaDto(modulo = "ia", tipo = "consultar_faq", parametros = buildJsonObject { put("numero", "5") })
        coEvery { llmClient.chatEstructurado(any(), any(), any(), any()) } returns
            ApiResult.Success(
                RespuestaIaDto(
                    respuestaUsuario = "El efectivo se cobra pidiendo el monto y tal y cual, respuesta larga improvisada.",
                    acciones = listOf(accion),
                ),
            )
        val ejecutor = mockk<EjecutorAccionesIa>()
        coEvery { ejecutor.ejecutar(any(), any(), any(), any()) } returns
            ResultadoAccionIa.Ejecutada("**Efectivo**: la app pide el monto recibido y calcula el cambio.")
        val viewModel = viewModel(llmClient = llmClient, ejecutorAccionesIa = ejecutor)

        viewModel.onTextoChange("como se cobra")
        viewModel.enviarMensaje()

        val iaMsgs = viewModel.uiState.value.mensajes.filterIsInstance<ChatUiMessage.DeIa>()
        assertTrue(iaMsgs.any { it.texto.contains("calcula el cambio") })
        assertTrue(iaMsgs.none { it.texto.contains("respuesta larga improvisada") })
    }

    // exportar_inventario es la unica accion que produce un archivo para
    // compartir - se expone en ChatUiState.archivoParaCompartir para que
    // AsistenteIaWidget dispare el mismo share sheet que InventarioScreen.
    @Test
    fun `exportar_inventario expone el archivo para compartir y se limpia al confirmarse compartido`() = runTest(dispatcher) {
        val llmClient = mockk<LlmClient>()
        val accion = AccionIaDto(modulo = "inventario", tipo = "exportar_inventario", parametros = buildJsonObject { })
        coEvery { llmClient.chatEstructurado(any(), any(), any(), any()) } returns
            ApiResult.Success(RespuestaIaDto(respuestaUsuario = "Exportando...", acciones = listOf(accion)))
        val archivo = ArchivoExportado(mockk(relaxed = true), "text/csv")
        val ejecutor = mockk<EjecutorAccionesIa>()
        coEvery { ejecutor.ejecutar(any(), any(), any(), any()) } returns
            ResultadoAccionIa.Ejecutada("Exporté 3 artículo(s) a CSV, listo para compartir.", archivoParaCompartir = archivo)
        val viewModel = viewModel(llmClient = llmClient, ejecutorAccionesIa = ejecutor)

        viewModel.onTextoChange("exportá el inventario completo")
        viewModel.enviarMensaje()

        assertEquals(archivo, viewModel.uiState.value.archivoParaCompartir)

        viewModel.onArchivoCompartido()

        assertEquals(null, viewModel.uiState.value.archivoParaCompartir)
    }

    @Test
    fun `rechazar una accion la marca como descartada sin ejecutar nada`() = runTest(dispatcher) {
        val llmClient = mockk<LlmClient>()
        val accion = AccionIaDto(modulo = "caja", tipo = "corte_parcial", parametros = buildJsonObject { })
        coEvery { llmClient.chatEstructurado(any(), any(), any(), any()) } returns
            ApiResult.Success(RespuestaIaDto(respuestaUsuario = "Listo.", acciones = listOf(accion)))
        val ejecutor = mockk<EjecutorAccionesIa>()
        val viewModel = viewModel(llmClient = llmClient, ejecutorAccionesIa = ejecutor)
        viewModel.onTextoChange("hace un corte parcial")
        viewModel.enviarMensaje()
        val pendiente = viewModel.uiState.value.mensajes.filterIsInstance<ChatUiMessage.AccionPendiente>().single()

        viewModel.rechazarAccion(pendiente.id)

        coVerify(exactly = 0) { ejecutor.ejecutar(any(), any(), any(), any()) }
        val resuelta = viewModel.uiState.value.mensajes.filterIsInstance<ChatUiMessage.AccionPendiente>().single()
        assertTrue(resuelta.resuelta)
        assertEquals("Acción descartada por el usuario.", resuelta.mensajeResultado)
    }

    // Hallazgo de code-reviewer: AsistenteIaWidget se monta una sola vez
    // fuera del `when(pantalla)` de MainActivity, asi que la misma instancia
    // de ChatViewModel sobrevive a un logout/login - sin este reset, el
    // historial (y cualquier AccionPendiente sin confirmar) de un usuario
    // quedaria visible, y confirmable, para el siguiente que inicie sesion
    // en el mismo dispositivo.
    @Test
    fun `cambiar de usuario en el mismo dispositivo limpia el chat y las acciones pendientes del usuario anterior`() = runTest(dispatcher) {
        val sessionFlow = MutableStateFlow<Session?>(sesion)
        val sessionManager = mockk<SessionManager>(relaxed = true)
        every { sessionManager.session } returns sessionFlow
        val llmClient = mockk<LlmClient>()
        val accion = AccionIaDto(modulo = "caja", tipo = "corte_parcial", parametros = buildJsonObject { })
        coEvery { llmClient.chatEstructurado(any(), any(), any(), any()) } returns
            ApiResult.Success(RespuestaIaDto(respuestaUsuario = "Listo.", acciones = listOf(accion)))
        val viewModel = viewModel(sessionManager = sessionManager, llmClient = llmClient)
        viewModel.abrirPanel()
        viewModel.onTextoChange("hace un corte parcial")
        viewModel.enviarMensaje()
        assertTrue(viewModel.uiState.value.mensajes.isNotEmpty())

        sessionFlow.value = Session(username = "otro-usuario", usuarioId = "u2", rolId = "r1")

        assertTrue(viewModel.uiState.value.mensajes.isEmpty())
        assertEquals(false, viewModel.uiState.value.abierto)
    }

    // PLAN.md Parte 16, sub-paso 3, items 1 y 2 (revision 2026-08-22): el
    // chat ejecuta acciones igual en los tres modos de backend - no hay
    // ninguna rama por BackendMode en ChatViewModel/EjecutorAccionesIa, asi
    // que se confirma explicitamente para los tres en vez de dejarlo
    // implicito en el resto de las pruebas (que usan LOCAL por defecto).
    @Test
    fun `una accion se ejecuta igual en los tres modos de backend, incluido LOCAL`() = runTest(dispatcher) {
        for (modo in BackendMode.entries) {
            val llmClient = mockk<LlmClient>()
            val accion = AccionIaDto(modulo = "caja", tipo = "corte_parcial", parametros = buildJsonObject { })
            coEvery { llmClient.chatEstructurado(any(), any(), any(), any()) } returns
                ApiResult.Success(RespuestaIaDto(respuestaUsuario = "Listo.", acciones = listOf(accion)))
            val ejecutor = mockk<EjecutorAccionesIa>()
            coEvery { ejecutor.ejecutar(any(), any(), any(), any()) } returns ResultadoAccionIa.Ejecutada("Corte parcial registrado.")
            val preferencesEnModo = mockk<ConfiguracionPreferences>()
            every { preferencesEnModo.deviceConfig } returns flowOf(
                DeviceConfig(backendMode = modo, ip = "", puerto = "", sucursalIdSeleccionada = "suc-1"),
            )
            val viewModel = viewModel(preferences = preferencesEnModo, llmClient = llmClient, ejecutorAccionesIa = ejecutor)
            viewModel.onTextoChange("hace un corte parcial")
            viewModel.enviarMensaje()
            val pendiente = viewModel.uiState.value.mensajes.filterIsInstance<ChatUiMessage.AccionPendiente>().single()

            viewModel.confirmarAccion(pendiente.id)

            coVerify { ejecutor.ejecutar(accion, setOf("ia", "entrada"), "suc-1", "german") }
        }
    }

    // PLAN.md Parte 16, sub-paso 3: deteccion reactiva - no hay chequeo
    // previo de conectividad, se distingue el "sin conexion" del resto de
    // los errores del proveedor por el mensaje exacto que ya distingue
    // LlmClient (MENSAJE_SIN_CONEXION_IA).
    @Test
    fun `si el proveedor no tiene conexion, el panel cae al FAQ en vez de mostrar un error mas`() = runTest(dispatcher) {
        val llmClient = mockk<LlmClient>()
        coEvery { llmClient.chatEstructurado(any(), any(), any(), any()) } returns ApiResult.Error(MENSAJE_SIN_CONEXION_IA)
        val viewModel = viewModel(llmClient = llmClient)

        viewModel.onTextoChange("hola")
        viewModel.enviarMensaje()

        assertTrue(viewModel.uiState.value.sinConexion)
        assertTrue(viewModel.uiState.value.mensajes.none { it is ChatUiMessage.DeError })
        assertEquals(false, viewModel.uiState.value.enviando)
    }

    @Test
    fun `un error del proveedor que no es de conexion se muestra como mensaje, no activa el fallback de FAQ`() = runTest(dispatcher) {
        val llmClient = mockk<LlmClient>()
        coEvery { llmClient.chatEstructurado(any(), any(), any(), any()) } returns
            ApiResult.Error("Token invalido o sin permiso para el proveedor seleccionado")
        val viewModel = viewModel(llmClient = llmClient)

        viewModel.onTextoChange("hola")
        viewModel.enviarMensaje()

        assertEquals(false, viewModel.uiState.value.sinConexion)
        assertTrue(viewModel.uiState.value.mensajes.any { it is ChatUiMessage.DeError })
    }

    @Test
    fun `reintentar conexion vuelve a habilitar el chat sin perder el historial`() = runTest(dispatcher) {
        val llmClient = mockk<LlmClient>()
        coEvery { llmClient.chatEstructurado(any(), any(), any(), any()) } returns ApiResult.Error(MENSAJE_SIN_CONEXION_IA)
        val viewModel = viewModel(llmClient = llmClient)
        viewModel.onTextoChange("hola")
        viewModel.enviarMensaje()
        assertTrue(viewModel.uiState.value.sinConexion)

        viewModel.reintentarConexion()

        assertEquals(false, viewModel.uiState.value.sinConexion)
        assertTrue(viewModel.uiState.value.mensajes.any { it is ChatUiMessage.DeUsuario })
    }

    // PLAN.md Parte 16, sub-paso 4: acceso manual al FAQ (boton "Ayuda" del
    // diseno aprobado en el sub-paso 1), independiente de sinConexion.
    @Test
    fun `mostrarFaq y ocultarFaq alternan la vista de ayuda manual`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.mostrarFaq()
        assertTrue(viewModel.uiState.value.verFaq)

        viewModel.ocultarFaq()
        assertEquals(false, viewModel.uiState.value.verFaq)
    }

    // Pedido explicito del usuario tras probar en el Xiaomi.
    @Test
    fun `limpiarHistorial vacia la conversacion sin tocar visible ni abierto`() = runTest(dispatcher) {
        val llmClient = mockk<LlmClient>()
        coEvery { llmClient.chatEstructurado(any(), any(), any(), any()) } returns
            ApiResult.Success(RespuestaIaDto(respuestaUsuario = "Listo.", acciones = emptyList()))
        val viewModel = viewModel(llmClient = llmClient)
        viewModel.abrirPanel()
        viewModel.onTextoChange("hola")
        viewModel.enviarMensaje()
        assertTrue(viewModel.uiState.value.mensajes.isNotEmpty())

        viewModel.limpiarHistorial()

        assertTrue(viewModel.uiState.value.mensajes.isEmpty())
        assertTrue(viewModel.uiState.value.abierto)
        assertTrue(viewModel.uiState.value.visible)
    }

    // Hallazgo de code-reviewer: si el usuario abre "Ayuda" (verFaq = true)
    // mientras un mensaje en vuelo termina fallando por conexion
    // (sinConexion = true), reintentarConexion() solo limpiaba sinConexion -
    // el usuario quedaba atrapado en la vista de FAQ tras tocar "Reintentar",
    // en vez de volver al chat.
    @Test
    fun `reintentar conexion tambien cierra la vista de ayuda manual si quedo abierta`() = runTest(dispatcher) {
        val llmClient = mockk<LlmClient>()
        coEvery { llmClient.chatEstructurado(any(), any(), any(), any()) } returns ApiResult.Error(MENSAJE_SIN_CONEXION_IA)
        val viewModel = viewModel(llmClient = llmClient)
        viewModel.mostrarFaq()
        viewModel.onTextoChange("hola")
        viewModel.enviarMensaje()
        assertTrue(viewModel.uiState.value.sinConexion)
        assertTrue(viewModel.uiState.value.verFaq)

        viewModel.reintentarConexion()

        assertEquals(false, viewModel.uiState.value.sinConexion)
        assertEquals(false, viewModel.uiState.value.verFaq)
    }
}
