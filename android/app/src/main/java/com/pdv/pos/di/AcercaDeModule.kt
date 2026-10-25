package com.pdv.pos.di

import android.content.Context
import com.pdv.pos.BuildConfig
import com.pdv.pos.R
import com.pdv.pos.acercade.InfoRelease
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AcercaDeModule {

    @Provides
    fun provideInfoRelease(@ApplicationContext context: Context): InfoRelease = InfoRelease(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        fecha = context.getString(R.string.release_fecha),
        novedades = context.resources.getStringArray(R.array.release_novedades).toList(),
        equipo = context.getString(R.string.acerca_equipo),
        contacto = context.getString(R.string.acerca_contacto),
    )
}
