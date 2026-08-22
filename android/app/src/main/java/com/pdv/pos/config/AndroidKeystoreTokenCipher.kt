package com.pdv.pos.config

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "pdv_ia_token_key"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_LENGTH_BITS = 128

// Cifra el token del asistente de IA con una clave AES-256 respaldada por
// Android Keystore (PLAN.md Parte 14, sub-paso 3): la clave se genera una
// sola vez dentro del keystore y nunca sale de ahi en claro; solo el
// ciphertext+IV viaja hacia DataStore. No es testeable en JVM
// (KeyStore.getInstance("AndroidKeyStore") requiere runtime Android real) -
// se verifica instalando en el Xiaomi (needs-device).
@Singleton
class AndroidKeystoreTokenCipher @Inject constructor() : TokenCipher {

    private val secretKey: SecretKey by lazy { obtenerOCrearClave() }

    override fun cifrar(texto: String): TokenCifrado {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val ciphertext = cipher.doFinal(texto.toByteArray(Charsets.UTF_8))
        return TokenCifrado(ciphertext = ciphertext, iv = cipher.iv)
    }

    override fun descifrar(token: TokenCifrado): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, token.iv))
        return String(cipher.doFinal(token.ciphertext), Charsets.UTF_8)
    }

    private fun obtenerOCrearClave(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return keyGenerator.generateKey()
    }
}
