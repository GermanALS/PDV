package com.pdv.pos.config

// Doble de prueba para TokenCipher: AndroidKeystoreTokenCipher no es
// testeable en JVM (PLAN.md Parte 14, sub-paso 3). XOR reversible alcanza
// para probar el contrato de IaPreferences (nunca persiste el texto plano,
// descifra al valor original) sin depender de Android Keystore real.
class FakeTokenCipher : TokenCipher {
    override fun cifrar(texto: String): TokenCifrado {
        val bytes = texto.toByteArray(Charsets.UTF_8).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        return TokenCifrado(ciphertext = bytes, iv = ByteArray(12))
    }

    override fun descifrar(token: TokenCifrado): String {
        val bytes = token.ciphertext.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        return String(bytes, Charsets.UTF_8)
    }
}
