package com.pdv.pos.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.Sucursal
import com.pdv.pos.domain.repository.AuthRepository
import com.pdv.pos.domain.repository.LoginResultado
import com.pdv.pos.domain.repository.RolRepository
import com.pdv.pos.domain.repository.SucursalRepository
import com.pdv.pos.ia.LlmClient
import com.pdv.pos.ia.LlmProvider
import com.pdv.pos.logging.AppLogger
import com.pdv.pos.logging.LogType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ConfiguracionViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val preferences: ConfiguracionPreferences,
    private val sucursalRepository: SucursalRepository,
    private val iaPreferences: IaPreferences,
    private val llmClient: LlmClient,
    private val promptIaPreferences: PromptIaPreferences,
    private val authRepository: AuthRepository,
    private val rolRepository: RolRepository,
    private val appLogger: AppLogger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConfiguracionUiState())
    val uiState: StateFlow<ConfiguracionUiState> = _uiState.asStateFlow()

    // Modulos habilitados para el rol de la sesion, para gatear el acceso
    // al panel de conflictos igual que "usuarios" (PLAN.md Parte 19).
    private val modulosPermitidos = MutableStateFlow<Set<String>>(emptySet())

    init {
        viewModelScope.launch {
            sessionManager.session
                .flatMapLatest { session ->
                    if (session == null) {
                        flowOf(emptySet<String>())
                    } else {
                        rolRepository.observeRoles().map { roles ->
                            roles.find { it.id == session.rolId }?.modulosPermitidos?.toSet() ?: emptySet()
                        }
                    }
                }
                .collect { modulos -> modulosPermitidos.value = modulos }
        }
        // Los campos de conexion (ip/puerto) se siembran una sola vez desde el
        // valor persistido y de ahi en mas son un borrador puramente local
        // hasta onGuardarConexion(): son preferencia de dispositivo de un solo
        // escritor (este ViewModel), asi que no hace falta mantenerlos
        // sincronizados con cada emision de deviceConfig. Re-derivarlos en cada
        // emision (como sucursal/modo) pisaria una edicion en curso del usuario
        // cada vez que cambia la sucursal o el modo (hallazgo de code-reviewer).
        viewModelScope.launch {
            val inicial = preferences.deviceConfig.first()
            _uiState.update { it.copy(ip = inicial.ip, puerto = inicial.puerto) }
        }
        viewModelScope.launch {
            combine(preferences.deviceConfig, sucursalRepository.observeSucursales()) { config, sucursales ->
                config to sucursales
            }.collect { (config, sucursales) ->
                val seleccionada = sucursales.find { sucursal -> sucursal.id == config.sucursalIdSeleccionada }
                    ?: sucursales.firstOrNull()
                // Si la sucursal persistida no existe en el catalogo del modo
                // actual (tipico al pasar de LOCAL a REMOTO sin tocar el
                // dropdown), persiste el fallback: sin esto el estado en memoria
                // muestra la sucursal correcta pero la primera escritura remota
                // falla con ForeignKeyViolationError contra el id local viejo.
                if (config.backendMode != BackendMode.LOCAL &&
                    seleccionada != null &&
                    seleccionada.id != config.sucursalIdSeleccionada
                ) {
                    preferences.setSucursalSeleccionada(seleccionada.id)
                }
                _uiState.update {
                    it.copy(
                        sucursales = sucursales,
                        sucursalSeleccionada = seleccionada,
                        modo = config.backendMode,
                    )
                }
            }
        }
        // Igual criterio que ip/puerto: se siembra una sola vez, de ahi en
        // mas es un borrador local hasta onGuardarPromptIa()
        // (evita pisar una edicion en curso, mismo hallazgo de code-reviewer
        // que motivo el mismo patron en la seccion de Conexion, PLAN.md
        // Parte 6 sub-paso 5).
        viewModelScope.launch {
            _uiState.update { it.copy(promptIa = promptIaPreferences.prompt.first()) }
        }
        viewModelScope.launch {
            iaPreferences.config.collect { config ->
                // iaTokenInput nunca se siembra desde aca: el campo de texto
                // del token siempre arranca vacio (PLAN.md Parte 14, sub-paso 2).
                _uiState.update {
                    it.copy(
                        iaActivo = config.activo,
                        iaProveedor = config.proveedor,
                        iaModelo = config.modelo.ifBlank { config.proveedor.modeloPorDefecto },
                        iaTieneTokenGuardado = config.tieneToken,
                    )
                }
            }
        }
    }

    // Compuerta de acceso al panel de conflictos, mismo criterio que el
    // modulo "usuarios" (PLAN.md Parte 19). Un intento denegado queda
    // registrado con categoria AUTH, igual que HelloViewModel.onIntentoNavegar.
    fun onIntentoAbrirConflictos(): Boolean {
        val permitido = "usuarios" in modulosPermitidos.value
        if (!permitido) {
            viewModelScope.launch {
                val sucursalId = preferences.deviceConfig.first().sucursalIdSeleccionada ?: "-"
                val username = sessionManager.session.value?.username
                if (username != null) {
                    appLogger.log(
                        LogType.AUTH,
                        sucursalId = sucursalId,
                        usuario = username,
                        mensaje = "Acceso denegado al panel de conflictos de sincronizacion",
                    )
                }
            }
        }
        return permitido
    }

    fun onIpChange(value: String) {
        _uiState.update { it.copy(ip = value) }
    }

    fun onPuertoChange(value: String) {
        _uiState.update { it.copy(puerto = value) }
    }

    fun onGuardarConexion() {
        val estado = _uiState.value
        viewModelScope.launch {
            preferences.setConexion(estado.ip, estado.puerto)
        }
    }

    fun onSucursalSelected(sucursal: Sucursal) {
        viewModelScope.launch {
            preferences.setSucursalSeleccionada(sucursal.id)
        }
    }

    fun onModoSelected(modo: BackendMode) {
        viewModelScope.launch {
            preferences.setBackendMode(modo)
        }
    }

    fun onIaActivoChange(activo: Boolean) {
        _uiState.update { it.copy(iaActivo = activo) }
    }

    fun onIaProveedorSelected(proveedor: LlmProvider) {
        // Cambiar de proveedor resetea el modelo a su default: los modelos
        // de un proveedor no existen en otro (PLAN.md Parte 14, sub-paso 2).
        _uiState.update {
            if (it.iaProveedor == proveedor) it else it.copy(iaProveedor = proveedor, iaModelo = proveedor.modeloPorDefecto)
        }
    }

    fun onIaModeloChange(value: String) {
        _uiState.update { it.copy(iaModelo = value) }
    }

    fun onIaTokenInputChange(value: String) {
        _uiState.update { it.copy(iaTokenInput = value) }
    }

    fun onGuardarIa() {
        val estado = _uiState.value
        viewModelScope.launch {
            iaPreferences.setActivo(estado.iaActivo)
            iaPreferences.setProveedor(estado.iaProveedor)
            iaPreferences.setModelo(estado.iaModelo)
            if (estado.iaTokenInput.isNotBlank()) {
                iaPreferences.setToken(estado.iaTokenInput)
                _uiState.update { it.copy(iaTokenInput = "") }
            }
        }
    }

    fun onProbarConexionIa() {
        val estado = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(iaProbandoConexion = true, iaResultadoPrueba = null) }
            val token = estado.iaTokenInput.ifBlank { iaPreferences.getToken().orEmpty() }
            val modelo = estado.iaModelo.ifBlank { estado.iaProveedor.modeloPorDefecto }
            val resultado = llmClient.probarConectividad(estado.iaProveedor, token, modelo)
            _uiState.update { it.copy(iaProbandoConexion = false, iaResultadoPrueba = resultado) }
        }
    }

    fun onPromptIaChange(value: String) {
        _uiState.update { it.copy(promptIa = value, promptIaError = null) }
    }

    fun onPromptIaPasswordChange(value: String) {
        _uiState.update { it.copy(promptIaPasswordInput = value, promptIaError = null) }
    }

    // Reautenticacion antes de persistir un cambio al prompt (PLAN.md
    // Parte 16, sub-paso 1, cierra el gate bloqueante de la Parte 15
    // Decision 3): un dispositivo desatendido y ya logueado no alcanza para
    // alterar los limites de la IA, hace falta reingresar la contrasena del
    // usuario de la sesion activa. Reusa AuthRepository.login existente, sin
    // endpoint ni camino de escritura nuevo.
    fun onGuardarPromptIa() {
        val estado = _uiState.value
        val username = sessionManager.session.value?.username
        if (username == null) {
            _uiState.update { it.copy(promptIaError = "No hay sesión activa.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(promptIaGuardando = true, promptIaError = null) }
            when (authRepository.login(username, estado.promptIaPasswordInput)) {
                is LoginResultado.Exitoso -> {
                    promptIaPreferences.setPrompt(estado.promptIa)
                    _uiState.update { it.copy(promptIaGuardando = false, promptIaPasswordInput = "") }
                }
                LoginResultado.CredencialesInvalidas -> {
                    _uiState.update {
                        it.copy(promptIaGuardando = false, promptIaError = "Contraseña incorrecta, cambio no guardado.")
                    }
                }
            }
        }
    }
}
