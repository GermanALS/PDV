package com.pdv.pos.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

// Claves que ninguna version vigente escribe ni lee (PLAN.md Parte 37, M-10):
// ia_token es la API key de IA en claro de un build de desarrollo previo al
// cifrado de la Parte 14; nombre_base_datos salio de la conexion; los cursores
// sin sucursal son el formato anterior a la Parte 34.
internal val CLAVES_OBSOLETAS = listOf(
    "ia_token",
    "nombre_base_datos",
    "sync_pull_cursor_inventario",
    "sync_pull_cursor_cortes_caja",
    "sync_pull_cursor_retiros_efectivo",
).map { stringPreferencesKey(it) }

@Singleton
class LimpiezaDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    // Se llama al arrancar el proceso; solo escribe si alguna clave sigue ahi,
    // asi que en la practica corre una sola vez por dispositivo.
    suspend fun limpiarClavesObsoletas() {
        val presentes = CLAVES_OBSOLETAS.filter { it in dataStore.data.first() }
        if (presentes.isNotEmpty()) {
            dataStore.edit { prefs -> presentes.forEach { prefs.remove(it) } }
        }
    }
}
