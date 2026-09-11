package com.pdv.pos.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val KEY_LAST_SUCCESS_AT = longPreferencesKey("sync_last_success_at")
private val KEY_LAST_ERROR = stringPreferencesKey("sync_last_error")

private fun pullCursorKey(entidad: String) = stringPreferencesKey("sync_pull_cursor_$entidad")

data class SyncState(
    val lastSuccessAtMillis: Long? = null,
    val lastError: String? = null,
)

// Estado del motor de sync diferido (PLAN.md Parte 32), en el mismo DataStore
// de dispositivo que ConfiguracionPreferences/IaPreferences. Guarda el
// resultado del ultimo ciclo (para la seccion "Sincronizacion" de
// Configuracion) y el cursor updated_since por entidad del pull.
@Singleton
class SyncStateStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val state: Flow<SyncState> = dataStore.data.map { prefs ->
        SyncState(
            lastSuccessAtMillis = prefs[KEY_LAST_SUCCESS_AT],
            lastError = prefs[KEY_LAST_ERROR],
        )
    }

    // Un ciclo correcto fija la marca de tiempo y limpia el ultimo error.
    suspend fun registrarExito(nowMillis: Long) {
        dataStore.edit {
            it[KEY_LAST_SUCCESS_AT] = nowMillis
            it.remove(KEY_LAST_ERROR)
        }
    }

    // Un ciclo con fallo guarda el mensaje; no toca la marca del ultimo exito.
    suspend fun registrarError(mensaje: String) {
        dataStore.edit { it[KEY_LAST_ERROR] = mensaje }
    }

    // Cursor del pull para una entidad: el updated_at (ISO-8601) del ultimo
    // registro traido, que se envia como ?updated_since= en el siguiente pull.
    suspend fun pullCursor(entidad: String): String? =
        dataStore.data.first()[pullCursorKey(entidad)]

    suspend fun setPullCursor(entidad: String, isoTimestamp: String) {
        dataStore.edit { it[pullCursorKey(entidad)] = isoTimestamp }
    }
}
