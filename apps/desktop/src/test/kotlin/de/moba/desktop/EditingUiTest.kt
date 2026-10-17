package de.moba.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import de.moba.adapters.catalog.StationCatalog
import de.moba.core.runtime.LicenseInfo
import de.moba.core.station.StationConfig
import de.moba.modules.driving.DrivingModule
import de.moba.modules.driving.ui.DrivingScreen
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
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Bearbeiten wie in iTrain: verschieben, Bereich kopieren und einfuegen, loeschen, Objekte wiederverwenden, Uhr. */
@OptIn(ExperimentalTestApi::class)
class EditingUiTest {
    private val plan = TrackPlan(
        "haupt", "Hauptbahnhof", 12, 6,
        listOf(
            TrackTile(1, 1, TrackShape.STRAIGHT, 2),
            TrackTile(2, 1, TrackShape.TURNOUT_LEFT, 2, address = 5),
            TrackTile(3, 1, TrackShape.STRAIGHT, 2),
            TrackTile(6, 4, TrackShape.TURNOUT_RIGHT, 2),
        ),
    )

    private fun app(): MobaApp = runBlocking {
        val store = object : TrackPlanStore {
            override fun load() = listOf(plan)
            override fun save(plans: List<TrackPlan>) = Unit
        }
        MobaApp(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            drivers = listOf(StationCatalog.simulator),
            moduleFactory = { listOf(DrivingModule(), TrackPlanModule(TrackPlanRepository(store))) },
            license = LicenseInfo.FREE,
        ).also { it.connect(StationConfig("simulator")).join() }
    }

    private fun MobaApp.plans() = session.value!!.host.service(TrackPlanService::class)!!
    private fun MobaApp.plan() = plans().plans.value.first { it.id == "haupt" }

    private val screens = listOf(DrivingScreen, TrackPlanScreen)

    @BeforeTest
    @AfterTest
    fun reset() {
        TrackPlanUiState.mode.value = TrackPlanMode.BETRIEB
        TrackPlanUiState.tileSize.value = 24
        TrackPlanUiState.message.value = null
        TrackPlanUiState.selected.value = null
        TrackPlanUiState.area.value = emptySet()
        TrackPlanUiState.clipboard.value = emptyList()
        TrackPlanUiState.editorMenu.value = null
        TrackPlanUiState.clockDialog.value = false
        TrackPlanUiState.editPlan.value = null
        TrackPlanUiState.tool.value = EditTool.Place(TrackShape.STRAIGHT)
    }

    private fun editor() {
        TrackPlanUiState.mode.value = TrackPlanMode.BEARBEITEN
        TrackPlanUiState.editPlan.value = "haupt"
    }

    @Test
    fun tileIsDraggedToAnotherPlace() = runComposeUiTest {
        val app = app()
        editor()
        TrackPlanUiState.tool.value = EditTool.Select
        setContent { MobaShell(app, screens, initialScreen = TrackPlanScreen.moduleId) }
        onNodeWithTag(EDITOR_GRID_TAG).performTouchInput {
            val c = 24.dp.toPx()
            swipe(Offset(6.5f * c, 4.5f * c), Offset(9.5f * c, 2.5f * c), durationMillis = 400)
        }
        waitForIdle()
        assertNull(app.plan().tileAt(6, 4))
        assertEquals(TrackShape.TURNOUT_RIGHT, app.plan().tileAt(9, 2)?.shape)
    }

    @Test
    fun areaIsCopiedPastedAndDeletedWithKeys() = runComposeUiTest {
        val app = app()
        editor()
        TrackPlanUiState.tool.value = EditTool.Area
        setContent { MobaShell(app, screens, initialScreen = TrackPlanScreen.moduleId) }
        val grid = onNodeWithTag(EDITOR_GRID_TAG)
        // Rahmen ueber die drei Gleise in Zeile 2 aufziehen
        grid.performTouchInput {
            val c = 24.dp.toPx()
            swipe(Offset(0.5f * c, 0.5f * c), Offset(3.5f * c, 1.5f * c), durationMillis = 400)
        }
        waitForIdle()
        assertEquals(setOf(1 to 1, 2 to 1, 3 to 1), TrackPlanUiState.area.value)
        onNodeWithText("3 Kacheln markiert").assertExists()
        onNode(hasText("Kopieren") and hasClickAction()).performClick()
        onNode(hasText("Einfügen (3)") and hasClickAction()).performClick()
        grid.performTouchInput { click(Offset(1.5f * 24.dp.toPx(), 3.5f * 24.dp.toPx())) }
        waitForIdle()
        assertEquals(TrackShape.TURNOUT_LEFT, app.plan().tileAt(2, 3)?.shape)
        assertEquals(5, app.plan().tileAt(2, 3)?.address)
        assertEquals(setOf(1 to 3, 2 to 3, 3 to 3), TrackPlanUiState.area.value)

        // Eingefuegtes mit der Tastatur eins nach rechts schieben und dann loeschen
        grid.performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        assertNull(app.plan().tileAt(1, 3))
        assertNotNull(app.plan().tileAt(4, 3))
        grid.performKeyInput { pressKey(Key.Delete) }
        waitForIdle()
        assertEquals(4, app.plan().tiles.size)
        assertTrue(TrackPlanUiState.area.value.isEmpty())
    }

    @Test
    fun existingObjectIsReused() = runComposeUiTest {
        val app = app()
        editor()
        TrackPlanUiState.tool.value = EditTool.Select
        TrackPlanUiState.selected.value = 6 to 4
        setContent { MobaShell(app, screens, initialScreen = TrackPlanScreen.moduleId) }
        onNodeWithText("Vorhandenes Objekt übernehmen …").performClick()
        onNode(hasText("Weiche links 5", substring = true) and hasClickAction()).performClick()
        waitForIdle()
        assertEquals(5, app.plan().tileAt(6, 4)?.address)
        onNodeWithText("Dasselbe Objekt auch in", substring = true).assertExists()
    }

    @Test
    fun planIsCopiedAndClockIsStarted() = runComposeUiTest {
        val app = app()
        editor()
        setContent { MobaShell(app, screens, initialScreen = TrackPlanScreen.moduleId) }
        onNodeWithContentDescription("Gleisbild kopieren").performClick()
        waitForIdle()
        assertEquals(listOf("Hauptbahnhof", "Hauptbahnhof (Kopie)"), app.plans().plans.value.map { it.name })
        onNodeWithContentDescription("Gleisbild nach vorn").performClick()
        waitForIdle()
        assertEquals("Hauptbahnhof (Kopie)", app.plans().plans.value.first().name)

        TrackPlanUiState.mode.value = TrackPlanMode.BETRIEB
        onNodeWithText("angehalten", substring = true).performClick()
        onNodeWithText("×12").performClick()
        onNodeWithText("Starten", substring = true).performClick()
        waitForIdle()
        assertTrue(app.plans().clock.value.running)
        assertEquals(12, app.plans().clock.value.factor)
    }
}
