package com.pdv.pos.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pdv.pos.config.ConfiguracionPreferences
import com.pdv.pos.data.local.LocalAuthRepository
import com.pdv.pos.data.remote.RemoteAuthRepository
import com.pdv.pos.domain.model.BackendMode
import com.pdv.pos.domain.model.Usuario
import com.pdv.pos.domain.repository.LoginResultado
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

class ModeAwareAuthRepositoryTest {

    private val usuario = Usuario(id = "usuario-1", username = "admin", nombreCompleto = "Admin", rolId = "rol-1", activo = true)

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
    fun `local con sincronizacion with invalid local credentials does not try the remote login`(@TempDir tempDir: File) =
        runTest {
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

    @Test
    fun `local con sincronizacion attaches the remote JWT when the backend is reachable`(@TempDir tempDir: File) =
        runTest {
            val local = mockk<LocalAuthRepository>()
            coEvery { local.login("admin", "admin123") } returns LoginResultado.Exitoso(usuario, accessToken = null)
            val remote = mockk<RemoteAuthRepository>()
            coEvery { remote.login("admin", "admin123") } returns LoginResultado.Exitoso(usuario, accessToken = "jwt-remoto")

            val preferences = preferences(tempDir)
            preferences.setBackendMode(BackendMode.LOCAL_CON_SINCRONIZACION)
            val repository = ModeAwareAuthRepository(local, remote, preferences)

            val resultado = repository.login("admin", "admin123")

            assertEquals(LoginResultado.Exitoso(usuario, accessToken = "jwt-remoto"), resultado)
        }

    @Test
    fun `local con sincronizacion keeps a tokenless session when the remote login throws`(@TempDir tempDir: File) =
        runTest {
            val local = mockk<LocalAuthRepository>()
            coEvery { local.login("admin", "admin123") } returns LoginResultado.Exitoso(usuario, accessToken = null)
            val remote = mockk<RemoteAuthRepository>()
            coEvery { remote.login("admin", "admin123") } throws IOException("host inalcanzable")

            val preferences = preferences(tempDir)
            preferences.setBackendMode(BackendMode.LOCAL_CON_SINCRONIZACION)
            val repository = ModeAwareAuthRepository(local, remote, preferences)

            val resultado = repository.login("admin", "admin123")

            assertEquals(LoginResultado.Exitoso(usuario, accessToken = null), resultado)
        }

    @Test
    fun `local con sincronizacion keeps a tokenless session when the remote returns invalid credentials`(
        @TempDir tempDir: File,
    ) = runTest {
        val local = mockk<LocalAuthRepository>()
        coEvery { local.login("admin", "admin123") } returns LoginResultado.Exitoso(usuario, accessToken = null)
        val remote = mockk<RemoteAuthRepository>()
        coEvery { remote.login("admin", "admin123") } returns LoginResultado.CredencialesInvalidas

        val preferences = preferences(tempDir)
        preferences.setBackendMode(BackendMode.LOCAL_CON_SINCRONIZACION)
        val repository = ModeAwareAuthRepository(local, remote, preferences)

        val resultado = repository.login("admin", "admin123") as LoginResultado.Exitoso

        assertNull(resultado.accessToken)
    }

    @Test
    fun `local puro never calls the remote repository`(@TempDir tempDir: File) = runTest {
        val local = mockk<LocalAuthRepository>()
        coEvery { local.login("admin", "admin123") } returns LoginResultado.Exitoso(usuario, accessToken = null)
        val remote = mockk<RemoteAuthRepository>()

        val preferences = preferences(tempDir)
        preferences.setBackendMode(BackendMode.LOCAL)
        val repository = ModeAwareAuthRepository(local, remote, preferences)

        repository.login("admin", "admin123")

        coVerify(exactly = 0) { remote.login(any(), any()) }
    }
}
