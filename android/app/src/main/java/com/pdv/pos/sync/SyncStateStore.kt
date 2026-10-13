package com.pdv.pos.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val KEY_LAST_SUCCESS_AT = longPreferencesKey("sync_last_success_at")
private val KEY_LAST_ERROR = stringPreferencesKey("sync_last_error")

private val PULL_OVERLAP: Duration = Duration.ofMinutes(2)

private fun pullCursorKey(entidad: String, sucursalId: String) =
    stringPreferencesKey("sync_pull_cursor_${entidad}_$sucursalId")

// v2: la v1 corrio con una definicion de "pendiente" que contaba movimientos de
// ventas descartadas (PLAN.md Parte 34); se vuelve a reconciliar una vez.
private fun reconciliadoKey(sucursalId: String) =
    booleanPreferencesKey("sync_inventario_reconciliado_v2_$sucursalId")

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

    // Cursor del pull para (entidad, sucursal): el updated_at (ISO-8601) del
    // ultimo registro traido. Es por sucursal para que un cambio de sucursal
    // no herede el cursor de la anterior (PLAN.md Parte 34, M-3).
    suspend fun pullCursor(entidad: String, sucursalId: String): String? =
        dataStore.data.first()[pullCursorKey(entidad, sucursalId)]

    // Valor para ?updated_since=: el cursor menos la ventana de solape, para no
    // saltar filas confirmadas tarde en el backend (api-contract.md 7.1).
    suspend fun pullDesde(entidad: String, sucursalId: String): String? =
        pullCursor(entidad, sucursalId)?.let { Instant.parse(it).minus(PULL_OVERLAP).toString() }

    // Reconciliacion unica del inventario por sucursal (PLAN.md Parte 34):
    // mientras no este marcada, el pull baja todo el inventario de la sucursal.
    suspend fun inventarioReconciliado(sucursalId: String): Boolean =
        dataStore.data.first()[reconciliadoKey(sucursalId)] ?: false

    suspend fun marcarInventarioReconciliado(sucursalId: String) {
        dataStore.edit { it[reconciliadoKey(sucursalId)] = true }
    }

    suspend fun setPullCursor(entidad: String, sucursalId: String, isoTimestamp: String) {
        dataStore.edit { it[pullCursorKey(entidad, sucursalId)] = isoTimestamp }
    }
}
