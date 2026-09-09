package com.pdv.pos.config

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.EsquemaConexion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ConfiguracionPreferencesTest {

    private fun preferences(tempDir: File): ConfiguracionPreferences {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tempDir, "test.preferences_pb") }
        )
        return ConfiguracionPreferences(dataStore)
    }

    @Test
    fun `default device config is local mode with empty connection fields`(@TempDir tempDir: File) = runTest {
        val config = preferences(tempDir).deviceConfig.first()

        assertEquals(BackendMode.LOCAL, config.backendMode)
        assertEquals(EsquemaConexion.HTTP, config.esquema)
        assertEquals("", config.ip)
        assertEquals("", config.puerto)
    }

    @Test
    fun `writes and rereads the backend mode`(@TempDir tempDir: File) = runTest {
        val preferences = preferences(tempDir)

        preferences.setBackendMode(BackendMode.REMOTO)

        assertEquals(BackendMode.REMOTO, preferences.deviceConfig.first().backendMode)
    }

    @Test
    fun `writes and rereads the connection params`(@TempDir tempDir: File) = runTest {
        val preferences = preferences(tempDir)

        preferences.setConexion(esquema = EsquemaConexion.HTTPS, ip = "192.168.1.10", puerto = "8000")

        val config = preferences.deviceConfig.first()
        assertEquals(EsquemaConexion.HTTPS, config.esquema)
        assertEquals("192.168.1.10", config.ip)
        assertEquals("8000", config.puerto)
    }

    @Test
    fun `writes and rereads the selected sucursal id`(@TempDir tempDir: File) = runTest {
        val preferences = preferences(tempDir)

        preferences.setSucursalSeleccionada("sucursal-123")

        assertEquals("sucursal-123", preferences.deviceConfig.first().sucursalIdSeleccionada)
    }
}
