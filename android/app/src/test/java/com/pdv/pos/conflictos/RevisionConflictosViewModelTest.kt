package com.pdv.pos.conflictos

import com.pdv.pos.domain.model.SyncConflict
import com.pdv.pos.domain.repository.SyncConflictRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RevisionConflictosViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repo(conflictos: List<SyncConflict>) = object : SyncConflictRepository {
        override fun observeConflictos(): Flow<List<SyncConflict>> = flowOf(conflictos)
    }

    private fun conflict(id: String, auto: Boolean, fecha: Long = 0L) = SyncConflict(
        id = id,
        entidad = "inventario",
        entidadLocalId = "e-$id",
        sucursalId = null,
        valorLocal = "{\"cantidad\":1}",
        valorRemoto = "{\"cantidad\":2}",
        valorResuelto = "{\"cantidad\":3}",
        politicaAplicada = "evento_aditivo",
        resueltoAutomaticamente = auto,
        fechaDeteccion = fecha,
    )

    @Test
    fun `maps repository conflicts into ConflictoUi with a formatted date`() = runTest(dispatcher) {
        val viewModel = RevisionConflictosViewModel(
            repo(listOf(conflict("c1", auto = false, fecha = 1_756_389_791_000L))),
        )

        val ui = viewModel.uiState.value.conflictos.single()
        assertEquals("inventario", ui.entidad)
        assertEquals("{\"cantidad\":3}", ui.valorResuelto)
        assertTrue(ui.fechaDeteccion.matches(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""")))
    }

    @Test
    fun `PENDIENTES filter keeps only conflicts not resolved automatically`() = runTest(dispatcher) {
        val viewModel = RevisionConflictosViewModel(
            repo(listOf(conflict("c1", auto = false), conflict("c2", auto = true))),
        )

        viewModel.onFiltroSelected(FiltroConflicto.PENDIENTES)

        val filtrados = viewModel.uiState.value.conflictosFiltrados
        assertEquals(1, filtrados.size)
        assertFalse(filtrados.single().resueltoAutomaticamente)
    }
}
