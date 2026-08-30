package com.pdv.pos.ia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.config.IaPreferences
import com.pdv.pos.config.PromptIaPreferences
import com.pdv.pos.data.remote.ApiResult
import com.pdv.pos.data.remote.dto.ChatMessageDto
import com.pdv.pos.domain.repository.RolRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

// Widget flotante de chat con IA (PLAN.md Parte 16, sub-paso 1). Modo LOCAL
// ejecuta acciones igual que remoto/local-con-sincronizacion (Decisiones
// abiertas de esta Parte, revision 2026-08-22): un solo camino de armado de
// mensaje para los tres modos de backend, sin rama aparte de solo-texto
// para LOCAL.
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val rolRepository: RolRepository,
    private val preferences: ConfiguracionPreferences,
    private val iaPreferences: IaPreferences,
    private val promptIaPreferences: PromptIaPreferences,
    private val estadoPuntoVentaBuilder: EstadoPuntoVentaBuilder,
    private val llmClient: LlmClient,
    private val ejecutorAccionesIa: EjecutorAccionesIa,
    private val faqRepository: FaqRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Contenido estatico, sin necesidad de corutina ni de vivir en
    // ChatUiState (PLAN.md Parte 16, sub-paso 4): se lee una sola vez al
    // crear el ViewModel, igual de disponible con o sin conexion.
    val faqTexto: String = faqRepository.textoAyuda

    // AsistenteIaWidget se monta una sola vez fuera del `when(pantalla)` de
    // MainActivity, asi que hiltViewModel() resuelve siempre a la misma
    // instancia de ChatViewModel durante toda la vida de la Activity - un
    // logout/login no la recrea. Sin este chequeo, el historial de chat (y
    // cualquier AccionPendiente sin confirmar) de un usuario quedaria visible
    // para el siguiente que inicie sesion en el mismo dispositivo, y
    // confirmarAccion() atribuiria la ejecucion al usuario nuevo (hallazgo de
    // code-reviewer).
    private var usuarioDeLaSesionActual: String? = null

    // Consultas de solo lectura (PLAN.md Parte 18, sub-parte D; Parte 20): a
    // diferencia de las 4 acciones de escritura, se ejecutan de inmediato al
    // proponerse, sin tarjeta AccionPendiente ni confirmacion del usuario.
    private val tiposSoloLectura = setOf("exportar_inventario", "consultar_stock", "consultar_faq")

    init {
        // Misma compuerta de permiso que el resto de los modulos (PLAN.md
        // Parte 13, HelloViewModel.observarModulosPermitidos): el widget no
        // se muestra si el usuario en turno no tiene "ia" en su rol.
        viewModelScope.launch {
            sessionManager.session
                .flatMapLatest { session ->
                    if (session == null) {
                        flowOf(session to false)
                    } else {
                        rolRepository.observeRoles().map { roles ->
                            session to ("ia" in (roles.find { it.id == session.rolId }?.modulosPermitidos.orEmpty()))
                        }
                    }
                }
                .collect { (session, visible) ->
                    if (session?.username != usuarioDeLaSesionActual) {
                        usuarioDeLaSesionActual = session?.username
                        _uiState.value = ChatUiState(visible = visible)
                    } else {
                        _uiState.update { it.copy(visible = visible, abierto = it.abierto && visible) }
                    }
                }
        }
    }

    fun abrirPanel() {
        _uiState.update { it.copy(abierto = true) }
    }

    fun cerrarPanel() {
        _uiState.update { it.copy(abierto = false) }
    }

    fun onTextoChange(value: String) {
        _uiState.update { it.copy(entradaTexto = value) }
    }

    fun enviarMensaje() {
        val estado = _uiState.value
        val texto = estado.entradaTexto.trim()
        if (texto.isEmpty() || estado.enviando) return

        val numeroFaq = numeroFaqInstantaneo(texto)
        if (numeroFaq != null) {
            responderFaqInstantaneo(texto, numeroFaq)
            return
        }

        // Coincidencia exacta (tras normalizar) con una pregunta del FAQ:
        // se responde sin llamar al LLM, igual que "qN" (PLAN.md Parte 20,
        // ronda de dispositivo 2026-08-30).
        val faqPorTexto = faqRepository.matchPorTexto(texto)
        if (faqPorTexto != null) {
            responderFaqInstantaneo(texto, faqPorTexto.faq)
            return
        }

        val historialPrevio = estado.mensajes.mapNotNull {
            when (it) {
                is ChatUiMessage.DeUsuario -> ChatMessageDto(role = "user", content = it.texto)
                is ChatUiMessage.DeIa -> ChatMessageDto(role = "assistant", content = it.texto)
                is ChatUiMessage.DeError, is ChatUiMessage.AccionPendiente -> null
            }
        }

        _uiState.update {
            it.copy(
                mensajes = it.mensajes + ChatUiMessage.DeUsuario(UUID.randomUUID().toString(), texto),
                entradaTexto = "",
                enviando = true,
            )
        }

        viewModelScope.launch {
            when (val respuesta = obtenerRespuesta(texto, historialPrevio)) {
                is RespuestaChat.Mensajes ->
                    _uiState.update { it.copy(mensajes = it.mensajes + respuesta.mensajes, enviando = false) }
                RespuestaChat.SinConexion ->
                    _uiState.update { it.copy(enviando = false, sinConexion = true) }
            }
        }
    }

    // Atajo "qN" (PLAN.md Parte 20): el usuario escribe q + numero de
    // pregunta y la app responde esa entrada del FAQ sin llamar al LLM -
    // funciona sin token, sin sucursal y sin conexion. Solo matchea la
    // cadena completa (q + hasta 3 digitos), no algo como "que es q3".
    private fun numeroFaqInstantaneo(texto: String): Int? =
        Regex("""^[qQ]\s?(\d{1,3})$""").find(texto.trim())?.groupValues?.get(1)?.toInt()

    private fun responderFaqInstantaneo(texto: String, numero: Int) {
        val respuesta = faqRepository.find(numero)?.answer
            ?: "No encontré la pregunta $numero en el FAQ."
        _uiState.update {
            it.copy(
                mensajes = it.mensajes +
                    ChatUiMessage.DeUsuario(UUID.randomUUID().toString(), texto) +
                    ChatUiMessage.DeIa(UUID.randomUUID().toString(), respuesta),
                entradaTexto = "",
            )
        }
    }

    // Deteccion reactiva de "sin conexion" (PLAN.md Parte 16, sub-paso 3,
    // Decisiones abiertas): no hay chequeo previo de conectividad - se
    // intenta la llamada real y, si LlmClient devuelve MENSAJE_SIN_CONEXION_IA
    // (unico caso que distingue de otros errores del proveedor), el panel
    // cae al FAQ en vez de mostrar el error generico como un mensaje mas.
    private sealed class RespuestaChat {
        data class Mensajes(val mensajes: List<ChatUiMessage>) : RespuestaChat()
        data object SinConexion : RespuestaChat()
    }

    private suspend fun obtenerRespuesta(texto: String, historial: List<ChatMessageDto>): RespuestaChat {
        val iaConfig = iaPreferences.config.first()
        if (!iaConfig.activo) {
            return mensajesDeError("La IA no está activada. Actívala en Configuración.")
        }
        val token = iaPreferences.getToken()
            ?: return mensajesDeError("Configura un token del proveedor en Configuración.")
        val sucursalId = preferences.deviceConfig.first().sucursalIdSeleccionada
            ?: return mensajesDeError("Selecciona una sucursal en Configuración.")

        // FORMATO_SALIDA_ACCIONES va despues del prompt editable, no en su
        // lugar (hallazgo de pruebas en el Xiaomi): sin el, el prompt en
        // prosa no alcanza para que el proveedor acierte las claves exactas
        // de "parametros" y Json.decodeFromString fallaba con
        // SerializationException en cualquier pedido de accion.
        // instruccionFaq va al final (PLAN.md Parte 20): la lista de
        // preguntas del FAQ curado, para que el modelo responda via la
        // consulta de solo lectura "consultar_faq" en vez de improvisar.
        val promptSistema = promptIaPreferences.prompt.first() + "\n\n" +
            FORMATO_SALIDA_ACCIONES + "\n\n" + faqRepository.instruccionFaq
        val estadoJson = estadoPuntoVentaBuilder.armarJson(sucursalId, historial)
        val mensajesLlm = listOf(ChatMessageDto(role = "system", content = promptSistema)) +
            historial +
            listOf(ChatMessageDto(role = "user", content = "$estadoJson\n\nMensaje del usuario: $texto"))

        val modelo = iaConfig.modelo.ifBlank { iaConfig.proveedor.modeloPorDefecto }
        return when (val resultado = llmClient.chatEstructurado(iaConfig.proveedor, token, modelo, mensajesLlm)) {
            is ApiResult.Success -> {
                val respuesta = resultado.data
                // Cuando la unica accion es consultar_faq, se descarta la
                // burbuja respuesta_usuario del modelo y se muestra solo el
                // texto verbatim del FAQ (PLAN.md Parte 20, ronda de
                // dispositivo 2026-08-30): sin esto el modelo improvisaba
                // una respuesta ademas del texto exacto.
                val soloFaq = respuesta.acciones.isNotEmpty() &&
                    respuesta.acciones.all { it.tipo == "consultar_faq" }
                val mensajesBase =
                    if (soloFaq) emptyList()
                    else listOf(ChatUiMessage.DeIa(UUID.randomUUID().toString(), respuesta.respuestaUsuario))
                // Las consultas de solo lectura (PLAN.md Parte 18, sub-parte
                // D; Parte 20) se ejecutan de inmediato, sin tarjeta de
                // confirmacion - las 4 acciones de escritura restantes siguen
                // el camino existente de AccionPendiente.
                val mensajesDeAcciones = respuesta.acciones.map { accion ->
                    if (accion.tipo in tiposSoloLectura) {
                        ejecutarConsultaAutomatica(accion, sucursalId)
                    } else {
                        ChatUiMessage.AccionPendiente(
                            id = UUID.randomUUID().toString(),
                            descripcion = descripcionAccion(accion),
                            accion = accion,
                        )
                    }
                }
                RespuestaChat.Mensajes(mensajesBase + mensajesDeAcciones)
            }
            is ApiResult.Error -> if (resultado.message == MENSAJE_SIN_CONEXION_IA) {
                RespuestaChat.SinConexion
            } else {
                mensajesDeError(resultado.message)
            }
        }
    }

    private fun mensajesDeError(texto: String): RespuestaChat.Mensajes =
        RespuestaChat.Mensajes(listOf(ChatUiMessage.DeError(UUID.randomUUID().toString(), texto)))

    // Mismo resuelto de sesion/sucursal/permisos que confirmarAccion(), pero
    // sincrono con la respuesta del LLM (sin tarjeta pendiente) - PLAN.md
    // Parte 18, sub-parte D. Si "exportar_inventario" devuelve un archivo, se
    // expone en ChatUiState para que AsistenteIaWidget dispare el share sheet.
    private suspend fun ejecutarConsultaAutomatica(accion: AccionIaDto, sucursalId: String): ChatUiMessage {
        val session = sessionManager.session.value
            ?: return ChatUiMessage.DeIa(UUID.randomUUID().toString(), "No se pudo completar la consulta: falta la sesión.")

        val modulosPermitidos = rolRepository.observeRoles().first()
            .find { it.id == session.rolId }?.modulosPermitidos?.toSet() ?: emptySet()

        val resultado = ejecutorAccionesIa.ejecutar(
            accion = accion,
            modulosPermitidos = modulosPermitidos,
            sucursalId = sucursalId,
            usuarioId = session.username,
        )
        if (resultado is ResultadoAccionIa.Ejecutada && resultado.archivoParaCompartir != null) {
            _uiState.update { it.copy(archivoParaCompartir = resultado.archivoParaCompartir) }
        }
        return ChatUiMessage.DeIa(UUID.randomUUID().toString(), resultado.mensaje)
    }

    // Punto de reintento manual desde la vista de FAQ (sub-paso 3): vuelve a
    // habilitar el chat para que el proximo enviarMensaje() intente de
    // nuevo, sin descartar el historial ya construido. Tambien limpia
    // verFaq: si el usuario abrio Ayuda mientras un mensaje estaba en vuelo
    // y ese mensaje termino fallando por conexion, sinConexion pasa a true
    // con verFaq todavia en true (sinConexion tiene prioridad visual en
    // AsistenteIaWidget, pero el flag de verFaq queda pendiente) - sin este
    // reset, "Reintentar" dejaba al usuario atrapado en la vista de Ayuda en
    // vez de devolverlo al chat (hallazgo de code-reviewer).
    fun reintentarConexion() {
        _uiState.update { it.copy(sinConexion = false, verFaq = false) }
    }

    // Acceso manual al FAQ (sub-paso 4, diseno aprobado del sub-paso 1:
    // boton de ayuda en el header), independiente de sinConexion.
    fun mostrarFaq() {
        _uiState.update { it.copy(verFaq = true) }
    }

    fun ocultarFaq() {
        _uiState.update { it.copy(verFaq = false) }
    }

    // Pedido explicito del usuario tras probar en el Xiaomi: vaciar la
    // conversacion sin cerrar el panel ni tocar sesion/permiso. Una accion
    // pendiente sin confirmar tambien se descarta - "borrar historial" es
    // una limpieza total, no selectiva.
    fun limpiarHistorial() {
        _uiState.update { it.copy(mensajes = emptyList()) }
    }

    // Cada accion se confirma/ejecuta o se descarta por separado, sin
    // afectar a las demas de la misma respuesta (PLAN.md Parte 15,
    // Decision 2).
    fun confirmarAccion(id: String) {
        val pendiente = _uiState.value.mensajes
            .filterIsInstance<ChatUiMessage.AccionPendiente>()
            .find { it.id == id && !it.resuelta } ?: return

        viewModelScope.launch {
            val session = sessionManager.session.value
            val sucursalId = preferences.deviceConfig.first().sucursalIdSeleccionada
            if (session == null || sucursalId == null) {
                resolverAccion(id, "No se pudo confirmar: falta sesión o sucursal.")
                return@launch
            }

            val modulosPermitidos = rolRepository.observeRoles().first()
                .find { it.id == session.rolId }?.modulosPermitidos?.toSet() ?: emptySet()

            val resultado = ejecutorAccionesIa.ejecutar(
                accion = pendiente.accion,
                modulosPermitidos = modulosPermitidos,
                sucursalId = sucursalId,
                // Mismo criterio que EntradaViewModel/VentaViewModel/etc.:
                // "usuarioId" en los repositorios de dominio es el username
                // de la sesion, no el UUID Session.usuarioId.
                usuarioId = session.username,
            )
            resolverAccion(id, resultado.mensaje)
        }
    }

    fun rechazarAccion(id: String) {
        resolverAccion(id, "Acción descartada por el usuario.")
    }

    fun onArchivoCompartido() {
        _uiState.update { it.copy(archivoParaCompartir = null) }
    }

    private fun resolverAccion(id: String, mensaje: String) {
        _uiState.update { estado ->
            estado.copy(
                mensajes = estado.mensajes.map {
                    if (it is ChatUiMessage.AccionPendiente && it.id == id) {
                        it.copy(resuelta = true, mensajeResultado = mensaje)
                    } else {
                        it
                    }
                },
            )
        }
    }
}
