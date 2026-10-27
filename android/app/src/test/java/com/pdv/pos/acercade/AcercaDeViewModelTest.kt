package com.pdv.pos.acercade

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AcercaDeViewModelTest {

    private val infoRelease = InfoRelease(
        versionName = "1.2.3",
        versionCode = 10203,
        fecha = "2026-11-01",
        novedades = listOf("Venta", "Caja"),
        limitaciones = listOf("Cantidades enteras"),
        equipo = "German y Susana ALS",
        contacto = "german.antoniols@gmail.com",
    )

    @Test
    fun `uiState shows versionName together with versionCode`() {
        val viewModel = AcercaDeViewModel(infoRelease)

        assertEquals("1.2.3 (versionCode 10203)", viewModel.uiState.value.version)
    }

    @Test
    fun `uiState exposes the release date, notes, limitations and team data unchanged`() {
        val estado = AcercaDeViewModel(infoRelease).uiState.value

        assertEquals("2026-11-01", estado.fecha)
        assertEquals(listOf("Venta", "Caja"), estado.novedades)
        assertEquals(listOf("Cantidades enteras"), estado.limitaciones)
        assertEquals("German y Susana ALS", estado.equipo)
        assertEquals("german.antoniols@gmail.com", estado.contacto)
    }
}
