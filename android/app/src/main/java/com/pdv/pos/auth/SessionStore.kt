package com.pdv.pos.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pdv.pos.config.TokenCipher
import com.pdv.pos.config.TokenCifrado
import kotlinx.coroutines.flow.first
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

// Persiste la sesion activa entre reinicios del proceso (PLAN.md Parte 32,
// D3): el SyncWorker corre con la app cerrada y necesita el JWT sin re-login
// interactivo. Sin refresh_token: al expirar/401 el AuthInterceptor cierra la
// sesion y la app pide re-login.
interface SessionStore {
    suspend fun cargar(): Session?
    suspend fun guardar(session: Session)
    suspend fun limpiar()

    // Default de SessionManager cuando no se cablea persistencia (tests que
    // no la ejercen). No es un artefacto de test: es el comportamiento
    // legitimo "sin almacenamiento".
    object NoOp : SessionStore {
        override suspend fun cargar(): Session? = null
        override suspend fun guardar(session: Session) = Unit
        override suspend fun limpiar() = Unit
    }
}

private val KEY_USERNAME = stringPreferencesKey("session_username")
private val KEY_USUARIO_ID = stringPreferencesKey("session_usuario_id")
private val KEY_ROL_ID = stringPreferencesKey("session_rol_id")
private val KEY_TOKEN_CIPHERTEXT = stringPreferencesKey("session_token_ciphertext")
private val KEY_TOKEN_IV = stringPreferencesKey("session_token_iv")

// username/usuarioId/rolId van en claro (no son secretos); el accessToken se
// cifra con TokenCipher (AES-GCM respaldado por Android Keystore en
// produccion, patron de IaPreferences / Parte 14). Un dispositivo en modo
// LOCAL persiste la sesion sin token.
@Singleton
class DataStoreSessionStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val tokenCipher: TokenCipher,
) : SessionStore {

    override suspend fun cargar(): Session? {
        val prefs = dataStore.data.first()
        val username = prefs[KEY_USERNAME] ?: return null
        val ciphertext = prefs[KEY_TOKEN_CIPHERTEXT]
        val iv = prefs[KEY_TOKEN_IV]
        val accessToken = if (ciphertext != null && iv != null) {
            tokenCipher.descifrar(
                TokenCifrado(
                    ciphertext = Base64.getDecoder().decode(ciphertext),
                    iv = Base64.getDecoder().decode(iv),
                ),
            )
        } else {
            null
        }
        return Session(
            username = username,
            usuarioId = prefs[KEY_USUARIO_ID].orEmpty(),
            rolId = prefs[KEY_ROL_ID].orEmpty(),
            accessToken = accessToken,
        )
    }

    override suspend fun guardar(session: Session) {
        dataStore.edit { prefs ->
            prefs[KEY_USERNAME] = session.username
            prefs[KEY_USUARIO_ID] = session.usuarioId
            prefs[KEY_ROL_ID] = session.rolId
            val token = session.accessToken
            if (token != null) {
                val cifrado = tokenCipher.cifrar(token)
                prefs[KEY_TOKEN_CIPHERTEXT] = Base64.getEncoder().encodeToString(cifrado.ciphertext)
                prefs[KEY_TOKEN_IV] = Base64.getEncoder().encodeToString(cifrado.iv)
            } else {
                prefs.remove(KEY_TOKEN_CIPHERTEXT)
                prefs.remove(KEY_TOKEN_IV)
            }
        }
    }

    override suspend fun limpiar() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_USERNAME)
            prefs.remove(KEY_USUARIO_ID)
            prefs.remove(KEY_ROL_ID)
            prefs.remove(KEY_TOKEN_CIPHERTEXT)
            prefs.remove(KEY_TOKEN_IV)
        }
    }
}
