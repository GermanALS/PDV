package com.pdv.pos.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.local.ArticuloEntity
import com.pdv.pos.data.local.InventarioDao
import com.pdv.pos.data.local.LocalInventarioRepository
import com.pdv.pos.data.remote.InventarioApiService
import com.pdv.pos.data.remote.RemoteInventarioRepository
import com.pdv.pos.data.remote.dto.AjusteInventarioDto
import com.pdv.pos.data.remote.dto.ArticuloDto
import com.pdv.pos.data.remote.dto.InventarioDto
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.EdicionArticulo
import com.pdv.pos.logging.AppLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.math.BigDecimal

class ModeAwareInventarioRepositoryTest {

    private fun preferences(tempDir: File): ConfiguracionPreferences {
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { File(tempDir, "test.preferences_pb") })
        return ConfiguracionPreferences(dataStore)
    }

    private fun edicionDeEjemplo() = EdicionArticulo(
        articuloId = "art-1",
        sucursalId = "suc-1",
        usuarioId = "german",
        nombre = "Refresco de cola 600ml",
        descripcion = null,
        categoria = "Bebidas",
        unidadMedida = "pieza",
        precioVenta = BigDecimal("19.00"),
        costo = null,
        nuevaCantidad = BigDecimal("30"),
        ubicacion = "Estante A1",
    )

    @Test
    fun `switching BackendMode in DataStore switches which repository handles actualizarArticulo`(@TempDir tempDir: File) = runTest {
        val dao = mockk<InventarioDao>()
        coEvery { dao.getArticulo("art-1") } returns ArticuloEntity(
            localId = "art-1",
            remoteId = null,
            codigoBarras = null,
            sku = "REF-001",
            nombre = "Refresco de cola 600ml",
            descripcion = null,
            categoria = "Bebidas",
            unidadMedida = "pieza",
            precioVenta = BigDecimal("18.50"),
            costo = null,
            activo = true,
            updatedAt = 1_700_000_000_000L,
            isSynced = true,
            deletedAt = null,
        )
        coEvery { dao.actualizarArticuloCompleto(any(), any(), any(), any(), any(), any()) } returns Unit
        val appLogger = mockk<AppLogger>()
        coEvery { appLogger.log(any(), any(), any(), any()) } returns Unit
        val local = LocalInventarioRepository(dao, appLogger)

        val api = mockk<InventarioApiService>()
        coEvery { api.ajustarArticulo(any(), any()) } returns AjusteInventarioDto(
            articulo = ArticuloDto(
                id = "art-1",
                sku = "REF-001",
                nombre = "Refresco de cola 600ml",
                unidadMedida = "pieza",
                precioVenta = "19.00",
                updatedAt = "2023-11-14T22:13:20Z",
            ),
            inventario = InventarioDto(
                id = "inv-1",
                sucursalId = "suc-1",
                articuloId = "art-1",
                cantidad = "30.000",
                updatedAt = "2023-11-14T22:13:20Z",
            ),
        )
        val remote = RemoteInventarioRepository(api, appLogger)

        val preferences = preferences(tempDir)
        val repository = ModeAwareInventarioRepository(local = local, remote = remote, preferences = preferences)

        repository.actualizarArticulo(edicionDeEjemplo())
        coVerify(exactly = 1) { dao.actualizarArticuloCompleto(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { api.ajustarArticulo(any(), any()) }

        preferences.setBackendMode(BackendMode.REMOTO)
        repository.actualizarArticulo(edicionDeEjemplo())

        coVerify(exactly = 1) { api.ajustarArticulo(any(), any()) }
        coVerify(exactly = 1) { dao.actualizarArticuloCompleto(any(), any(), any(), any(), any(), any()) }
    }
}
