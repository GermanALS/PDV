package com.pdv.pos.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LlmNetworkModule {

    @Provides
    @Singleton
    @SinLoggingHttpClient
    fun provideOkHttpClientSinLogging(): OkHttpClient = OkHttpClient.Builder().build()
}
