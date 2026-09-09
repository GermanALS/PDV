package com.pdv.pos.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.EsquemaConexion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val KEY_BACKEND_MODE = stringPreferencesKey("backend_mode")
private val KEY_ESQUEMA = stringPreferencesKey("esquema")
private val KEY_IP = stringPreferencesKey("ip")
private val KEY_PUERTO = stringPreferencesKey("puerto")
private val KEY_SUCURSAL_ID = stringPreferencesKey("sucursal_id_seleccionada")

// Preferencia de dispositivo, sin Room (excepcion documentada en CLAUDE.md
// sec. 3: BackendMode, conexion y sucursal seleccionada viven en DataStore;
// el catalogo de sucursales en si sigue el patron Room normal).
@Singleton
class ConfiguracionPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val deviceConfig: Flow<DeviceConfig> = dataStore.data.map { prefs ->
        DeviceConfig(
            backendMode = prefs[KEY_BACKEND_MODE]?.let { BackendMode.valueOf(it) } ?: BackendMode.LOCAL,
            esquema = prefs[KEY_ESQUEMA]?.let { EsquemaConexion.valueOf(it) } ?: EsquemaConexion.HTTP,
            ip = prefs[KEY_IP].orEmpty(),
            puerto = prefs[KEY_PUERTO].orEmpty(),
            sucursalIdSeleccionada = prefs[KEY_SUCURSAL_ID],
        )
    }

    suspend fun setBackendMode(mode: BackendMode) {
        dataStore.edit { it[KEY_BACKEND_MODE] = mode.name }
    }

    suspend fun setConexion(esquema: EsquemaConexion, ip: String, puerto: String) {
        dataStore.edit {
            it[KEY_ESQUEMA] = esquema.name
            it[KEY_IP] = ip
            it[KEY_PUERTO] = puerto
        }
    }

    suspend fun setSucursalSeleccionada(sucursalId: String) {
        dataStore.edit { it[KEY_SUCURSAL_ID] = sucursalId }
    }
}
