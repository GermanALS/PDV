package com.pdv.pos.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.local.LocalAuthRepository
import com.pdv.pos.data.remote.RemoteAuthRepository
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.repository.LoginResultado
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ModeAwareAuthRepositoryTest {

    private fun preferences(tempDir: File): ConfiguracionPreferences {
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { File(tempDir, "test.preferences_pb") })
        return ConfiguracionPreferences(dataStore)
    }

    @Test
    fun `switching BackendMode in DataStore switches which repository handles login`(@TempDir tempDir: File) = runTest {
        val local = mockk<LocalAuthRepository>()
        coEvery { local.login("admin", "admin123") } returns LoginResultado.CredencialesInvalidas
        val remote = mockk<RemoteAuthRepository>()
        coEvery { remote.login("admin", "admin123") } returns LoginResultado.CredencialesInvalidas

        val preferences = preferences(tempDir)
        val repository = ModeAwareAuthRepository(local, remote, preferences)

        repository.login("admin", "admin123")
        coVerify(exactly = 1) { local.login("admin", "admin123") }
        coVerify(exactly = 0) { remote.login(any(), any()) }

        preferences.setBackendMode(BackendMode.REMOTO)
        repository.login("admin", "admin123")

        coVerify(exactly = 1) { remote.login("admin", "admin123") }
        coVerify(exactly = 1) { local.login("admin", "admin123") }
    }

    @Test
    fun `local con sincronizacion routes login through the local repository`(@TempDir tempDir: File) = runTest {
        val local = mockk<LocalAuthRepository>()
        coEvery { local.login(any(), any()) } returns LoginResultado.CredencialesInvalidas
        val remote = mockk<RemoteAuthRepository>()

        val preferences = preferences(tempDir)
        preferences.setBackendMode(BackendMode.LOCAL_CON_SINCRONIZACION)
        val repository = ModeAwareAuthRepository(local, remote, preferences)

        val resultado = repository.login("admin", "admin123")

        assertEquals(LoginResultado.CredencialesInvalidas, resultado)
        coVerify(exactly = 1) { local.login("admin", "admin123") }
        coVerify(exactly = 0) { remote.login(any(), any()) }
    }
}
