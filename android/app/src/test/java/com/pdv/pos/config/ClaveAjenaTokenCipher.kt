package com.pdv.pos.config

import javax.crypto.AEADBadTagException

// Doble de prueba para A-5 (PLAN.md Parte 37): cifra como FakeTokenCipher pero
// falla al descifrar, igual que AndroidKeystoreTokenCipher cuando los archivos
// llegaron de otra instalacion y la clave de Keystore no es la que cifro.
class ClaveAjenaTokenCipher : TokenCipher {
    private val cifrador = FakeTokenCipher()

    override fun cifrar(texto: String): TokenCifrado = cifrador.cifrar(texto)

    override fun descifrar(token: TokenCifrado): String =
        throw AEADBadTagException("Signature/MAC verification failed")
}
