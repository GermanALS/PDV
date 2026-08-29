package com.pdv.pos.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.pdv.pos.data.remote.AuthApiService
import com.pdv.pos.data.remote.CajaApiService
import com.pdv.pos.data.remote.DevolucionApiService
import com.pdv.pos.data.remote.DynamicHostInterceptor
import com.pdv.pos.data.remote.EntradaApiService
import com.pdv.pos.data.remote.HealthApiService
import com.pdv.pos.data.remote.InventarioApiService
import com.pdv.pos.data.remote.RetiroApiService
import com.pdv.pos.data.remote.RolApiService
import com.pdv.pos.data.remote.SucursalApiService
import com.pdv.pos.data.remote.SyncConflictApiService
import com.pdv.pos.data.remote.UsuarioApiService
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

// Host por defecto cuando no hay IP/Puerto guardados en Configuracion:
// requiere `adb reverse tcp:8000 tcp:8000` para llegar al backend local
// desde el dispositivo fisico por USB (en el emulador, 10.0.2.2). Con
// IP/Puerto guardados, DynamicHostInterceptor reescribe el host de cada
// request y este valor solo aporta el esquema y el path base.
private const val BASE_URL = "http://localhost:8000/api/v1/"

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideOkHttpClient(dynamicHostInterceptor: DynamicHostInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(dynamicHostInterceptor)
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
    fun provideSyncConflictApiService(retrofit: Retrofit): SyncConflictApiService =
        retrofit.create(SyncConflictApiService::class.java)

    @Provides
    @Singleton
    fun provideEntradaApiService(retrofit: Retrofit): EntradaApiService =
        retrofit.create(EntradaApiService::class.java)

    @Provides
    @Singleton
    fun provideInventarioApiService(retrofit: Retrofit): InventarioApiService =
        retrofit.create(InventarioApiService::class.java)

    @Provides
    @Singleton
    fun provideCajaApiService(retrofit: Retrofit): CajaApiService =
        retrofit.create(CajaApiService::class.java)

    @Provides
    @Singleton
    fun provideRetiroApiService(retrofit: Retrofit): RetiroApiService =
        retrofit.create(RetiroApiService::class.java)

    @Provides
    @Singleton
    fun provideDevolucionApiService(retrofit: Retrofit): DevolucionApiService =
        retrofit.create(DevolucionApiService::class.java)

    @Provides
    @Singleton
    fun provideUsuarioApiService(retrofit: Retrofit): UsuarioApiService =
        retrofit.create(UsuarioApiService::class.java)

    @Provides
    @Singleton
    fun provideRolApiService(retrofit: Retrofit): RolApiService =
        retrofit.create(RolApiService::class.java)

    @Provides
    @Singleton
    fun provideAuthApiService(retrofit: Retrofit): AuthApiService =
        retrofit.create(AuthApiService::class.java)
}
