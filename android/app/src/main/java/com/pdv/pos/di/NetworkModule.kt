package com.pdv.pos.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.pdv.pos.data.remote.EntradaApiService
import com.pdv.pos.data.remote.HealthApiService
import com.pdv.pos.data.remote.InventarioApiService
import com.pdv.pos.data.remote.SucursalApiService
import com.pdv.pos.data.remote.VentaApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Singleton

// Requiere `adb reverse tcp:8000 tcp:8000` para llegar al backend local desde
// el dispositivo fisico por USB. En el emulador, reemplazar por 10.0.2.2.
private const val BASE_URL = "http://localhost:8000/api/v1/"

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideHealthApiService(retrofit: Retrofit): HealthApiService =
        retrofit.create(HealthApiService::class.java)

    @Provides
    @Singleton
    fun provideSucursalApiService(retrofit: Retrofit): SucursalApiService =
        retrofit.create(SucursalApiService::class.java)

    @Provides
    @Singleton
    fun provideVentaApiService(retrofit: Retrofit): VentaApiService =
        retrofit.create(VentaApiService::class.java)

    @Provides
    @Singleton
    fun provideEntradaApiService(retrofit: Retrofit): EntradaApiService =
        retrofit.create(EntradaApiService::class.java)

    @Provides
    @Singleton
    fun provideInventarioApiService(retrofit: Retrofit): InventarioApiService =
        retrofit.create(InventarioApiService::class.java)
}
