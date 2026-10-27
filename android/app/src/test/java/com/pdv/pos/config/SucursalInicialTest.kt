package com.pdv.pos.config

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.Sucursal
import com.pdv.pos.domain.repository.SucursalRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SucursalInicialTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val catalogo = object : SucursalRepository {
        override fun observeSucursales(): Flow<List<Sucursal>> =
            flowOf(listOf(Sucursal(id = "local-principal", nombre = "Sucursal principal")))
    }

    private fun preferences(tempDir: File) = ConfiguracionPreferences(
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher + SupervisorJob()),
            produceFile = { File(tempDir, "config.preferences_pb") },
        ),
    )

    @Test
    fun `a fresh LOCAL install persists the default sucursal`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)

        SucursalInicial(preferences, catalogo).preseleccionarSiFalta()

        assertEquals("local-principal", preferences.deviceConfig.first().sucursalIdSeleccionada)
    }

    @Test
    fun `a saved sucursal is kept`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setSucursalSeleccionada("elegida")

        SucursalInicial(preferences, catalogo).preseleccionarSiFalta()

        assertEquals("elegida", preferences.deviceConfig.first().sucursalIdSeleccionada)
    }

    @Test
    fun `a mode with a backend is left to Configuracion`(@TempDir tempDir: File) = runTest(dispatcher) {
        val preferences = preferences(tempDir)
        preferences.setBackendMode(BackendMode.LOCAL_CON_SINCRONIZACION)

        SucursalInicial(preferences, catalogo).preseleccionarSiFalta()

        assertNull(preferences.deviceConfig.first().sucursalIdSeleccionada)
    }
}
