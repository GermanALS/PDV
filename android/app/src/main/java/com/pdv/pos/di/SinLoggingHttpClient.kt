package com.pdv.pos.di

import javax.inject.Qualifier

// Distingue el OkHttpClient del cliente LLM (PLAN.md Parte 14: el header
// Authorization con el token del usuario nunca debe llegar a Logcat) del
// OkHttpClient con HttpLoggingInterceptor que usa el resto del proyecto
// contra nuestro propio backend (di/NetworkModule.kt).
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SinLoggingHttpClient
