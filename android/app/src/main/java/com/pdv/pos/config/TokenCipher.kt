package com.pdv.pos.config

data class TokenCifrado(val ciphertext: ByteArray, val iv: ByteArray)

// Interfaz de cifrado del token de IA (PLAN.md Parte 14, sub-paso 3). Existe
// como interfaz - y no un metodo directo en IaPreferences - porque la
// implementacion real (AndroidKeystoreTokenCipher) usa Android Keystore, que
// no esta disponible en la JVM de los tests unitarios; los tests inyectan un
// TokenCipher falso para verificar que IaPreferences nunca persiste texto
// plano, sin depender de runtime Android real.
interface TokenCipher {
    fun cifrar(texto: String): TokenCifrado
    fun descifrar(token: TokenCifrado): String
}
