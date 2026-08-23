package com.pdv.pos.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pdv.pos.ia.PROMPT_SISTEMA_DEFAULT
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val KEY_PROMPT_SISTEMA = stringPreferencesKey("ia_prompt_sistema")

// Preferencia de dispositivo, sin Room (mismo criterio que IaPreferences).
// El texto no es un secreto (a diferencia del token) - se guarda en claro.
// Editar un valor ya guardado exige reautenticacion del administrador
// (PLAN.md Parte 16, sub-paso 1, cierra el gate bloqueante de la Parte 15
// Decision 3) - esa validacion vive en ConfiguracionViewModel, no aca.
@Singleton
class PromptIaPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val prompt: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_PROMPT_SISTEMA] ?: PROMPT_SISTEMA_DEFAULT
    }

    suspend fun setPrompt(texto: String) {
        dataStore.edit { it[KEY_PROMPT_SISTEMA] = texto }
    }
}
