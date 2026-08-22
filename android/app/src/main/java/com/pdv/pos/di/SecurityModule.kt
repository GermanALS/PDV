package com.pdv.pos.di

import com.pdv.pos.config.AndroidKeystoreTokenCipher
import com.pdv.pos.config.TokenCipher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    // AndroidKeystoreTokenCipher es la unica implementacion en produccion;
    // el binding via interfaz permite inyectar un TokenCipher falso en tests
    // (PLAN.md Parte 14, sub-paso 3).
    @Binds
    abstract fun bindTokenCipher(impl: AndroidKeystoreTokenCipher): TokenCipher
}
