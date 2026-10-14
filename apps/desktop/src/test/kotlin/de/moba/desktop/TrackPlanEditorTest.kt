package de.moba.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import de.moba.adapters.catalog.StationCatalog
import de.moba.core.runtime.LicenseInfo
import de.moba.core.station.StationConfig
import de.moba.modules.trackplan.TrackPlanModule
import de.moba.modules.trackplan.TrackPlanRepository
import de.moba.modules.trackplan.api.TrackPlan
import de.moba.modules.trackplan.api.TrackPlanService
import de.moba.modules.trackplan.api.TrackPlanStore
import de.moba.modules.trackplan.api.TrackShape
import de.moba.modules.trackplan.api.TrackTile
import de.moba.modules.trackplan.ui.EDITOR_GRID_TAG
import de.moba.modules.trackplan.ui.EditTool
import de.moba.modules.trackplan.ui.TrackPlanMode
import de.moba.modules.trackplan.ui.TrackPlanScreen
import de.moba.modules.trackplan.ui.TrackPlanUiState
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.MobaShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Bedient den Gleisbild-Editor wie ein Nutzer: mehrere Klicks nacheinander, neues Gleisbild, Speichern. */
@OptIn(ExperimentalTestApi::class)
class TrackPlanEditorTest {
    private class MemoryStore(start: List<TrackPlan>) : TrackPlanStore {
        var saved: List<TrackPlan> = start
        override fun load() = saved
        override fun save(plans: List<TrackPlan>) {
            saved = plans
        }
    }

    private val start = TrackPlan(
        "test",
        "Testanlage",
        width = 8,
        height = 6,
        tiles = (1..4).map { TrackTile(it, 1, TrackShape.STRAIGHT, 2) },
    )

    private fun app(store: TrackPlanStore): Pair<MobaApp, TrackPlanService> = runBlocking {
        val app = MobaApp(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            drivers = listOf(StationCatalog.simulator),
            moduleFactory = { listOf(TrackPlanModule(TrackPlanRepository(store))) },
            license = LicenseInfo.of("basis", "pro", "club"),
        )
        app.connect(StationConfig("simulator")).join()
        app to app.session.value!!.host.service(TrackPlanService::class)!!
    }

    @AfterTest
    fun reset() {
        TrackPlanUiState.mode.value = TrackPlanMode.BETRIEB
        TrackPlanUiState.editPlan.value = null
        TrackPlanUiState.selected.value = null
        TrackPlanUiState.cellSize.value = null
        TrackPlanUiState.tool.value = EditTool.Place(TrackShape.STRAIGHT)
        TrackPlanUiState.message.value = null
    }

    private fun SemanticsNodeInteraction.tapCell(x: Int, y: Int, cellPx: Float) =
        performTouchInput { click(Offset(cellPx * (x + 0.5f), cellPx * (y + 0.5f))) }

    @Test
    fun editingSeveralTilesInARowKeepsEveryChange() = runComposeUiTest {
        val store = MemoryStore(listOf(start))
        val (app, service) = app(store)
        TrackPlanUiState.mode.value = TrackPlanMode.BEARBEITEN
        TrackPlanUiState.cellSize.value = CELL
        TrackPlanUiState.tool.value = EditTool.Erase
        setContent { MobaShell(app, listOf(TrackPlanScreen), emptyList(), initialScreen = TrackPlanScreen.moduleId) }
        val cellPx = CELL * density.density
        val grid = onNodeWithTag(EDITOR_GRID_TAG)

        // Zwei Gleise nacheinander entfernen: das erste darf nicht zurueckkommen.
        grid.tapCell(1, 1, cellPx)
        waitForIdle()
        grid.tapCell(2, 1, cellPx)
        waitForIdle()
        var plan = service.plans.value.single { it.id == "test" }
        assertNull(plan.tileAt(1, 1))
        assertNull(plan.tileAt(2, 1))
        assertNotNull(plan.tileAt(3, 1))

        // Zwei Gleise nacheinander setzen
        TrackPlanUiState.tool.value = EditTool.Place(TrackShape.STRAIGHT)
        waitForIdle()
        grid.tapCell(1, 3, cellPx)
        waitForIdle()
        grid.tapCell(2, 3, cellPx)
        waitForIdle()
        plan = service.plans.value.single { it.id == "test" }
        assertNotNull(plan.tileAt(1, 3))
        assertNotNull(plan.tileAt(2, 3))
        assertNull(plan.tileAt(1, 1))

        // Neues Gleisbild anlegen und darin zeichnen, das erste bleibt unveraendert
        onNodeWithText("Neues Gleisbild").performClick()
        waitForIdle()
        assertEquals(2, service.plans.value.size)
        val created = service.plans.value.last()
        assertEquals(created.id, TrackPlanUiState.editPlan.value)
        TrackPlanUiState.cellSize.value = CELL
        waitForIdle()
        grid.tapCell(0, 0, cellPx)
        waitForIdle()
        assertNotNull(service.plans.value.single { it.id == created.id }.tileAt(0, 0))
        assertNull(service.plans.value.single { it.id == "test" }.tileAt(0, 0))

        // Speichern schreibt beide Gleisbilder mit allen Aenderungen
        onNodeWithText("Speichern").performClick()
        waitForIdle()
        assertFalse(service.unsaved.value)
        assertEquals(listOf("test", created.id), store.saved.map { it.id })
        val saved = store.saved.first()
        assertNull(saved.tileAt(1, 1))
        assertNull(saved.tileAt(2, 1))
        assertNotNull(saved.tileAt(2, 3))
        assertNotNull(store.saved.last().tileAt(0, 0))
    }

    private companion object {
        const val CELL = 40
    }
}
