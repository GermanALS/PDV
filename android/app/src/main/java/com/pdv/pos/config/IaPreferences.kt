package com.pdv.pos.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pdv.pos.ia.LlmProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

private val KEY_IA_ACTIVO = booleanPreferencesKey("ia_activo")
private val KEY_IA_PROVEEDOR = stringPreferencesKey("ia_proveedor")
private val KEY_IA_MODELO = stringPreferencesKey("ia_modelo")
private val KEY_IA_TOKEN_CIPHERTEXT = stringPreferencesKey("ia_token_ciphertext")
private val KEY_IA_TOKEN_IV = stringPreferencesKey("ia_token_iv")

data class IaConfig(
    val activo: Boolean = false,
    val proveedor: LlmProvider = LlmProvider.DEEP_SEEK,
    val modelo: String = "",
    val tieneToken: Boolean = false,
)

// Preferencia de dispositivo, sin Room (mismo criterio que BackendMode en
// ConfiguracionPreferences). El token se cifra con TokenCipher (AES-GCM
// respaldado por Android Keystore en produccion, PLAN.md Parte 14,
// sub-paso 3) antes de escribirse: DataStore solo ve ciphertext+IV en
// Base64, nunca el token en claro.
@Singleton
class IaPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val tokenCipher: TokenCipher,
) {
    val config: Flow<IaConfig> = dataStore.data.map { prefs ->
        IaConfig(
            activo = prefs[KEY_IA_ACTIVO] ?: false,
            proveedor = prefs[KEY_IA_PROVEEDOR]?.let { LlmProvider.valueOf(it) } ?: LlmProvider.DEEP_SEEK,
            modelo = prefs[KEY_IA_MODELO].orEmpty(),
            tieneToken = !prefs[KEY_IA_TOKEN_CIPHERTEXT].isNullOrBlank(),
        )
    }

    // Solo para uso programatico (llamar al proveedor) - nunca asignar el
    // resultado a un campo de texto visible (PLAN.md Parte 14, sub-paso 2).
    suspend fun getToken(): String? {
        val prefs = dataStore.data.first()
        val ciphertext = prefs[KEY_IA_TOKEN_CIPHERTEXT] ?: return null
        val iv = prefs[KEY_IA_TOKEN_IV] ?: return null
        return tokenCipher.descifrar(
            TokenCifrado(ciphertext = Base64.getDecoder().decode(ciphertext), iv = Base64.getDecoder().decode(iv)),
        )
    }

    suspend fun setActivo(activo: Boolean) {
        dataStore.edit { it[KEY_IA_ACTIVO] = activo }
    }

    suspend fun setProveedor(proveedor: LlmProvider) {
        dataStore.edit { it[KEY_IA_PROVEEDOR] = proveedor.name }
    }

    suspend fun setModelo(modelo: String) {
        dataStore.edit { it[KEY_IA_MODELO] = modelo }
    }

    suspend fun setToken(token: String) {
        val cifrado = tokenCipher.cifrar(token)
        dataStore.edit {
            it[KEY_IA_TOKEN_CIPHERTEXT] = Base64.getEncoder().encodeToString(cifrado.ciphertext)
            it[KEY_IA_TOKEN_IV] = Base64.getEncoder().encodeToString(cifrado.iv)
        }
    }
}
