package de.moba.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import de.moba.adapters.catalog.StationCatalog
import de.moba.core.model.Loco
import de.moba.core.runtime.LicenseInfo
import de.moba.core.station.StationConfig
import de.moba.modules.driving.DrivingModule
import de.moba.modules.driving.ui.DrivingScreen
import de.moba.modules.trackplan.TrackPlanModule
import de.moba.modules.trackplan.TrackPlanRepository
import de.moba.modules.trackplan.api.AccessoryProps
import de.moba.modules.trackplan.api.BlockReservation
import de.moba.modules.trackplan.api.TrackPlan
import de.moba.modules.trackplan.api.TrackPlanService
import de.moba.modules.trackplan.api.TrackPlanStore
import de.moba.modules.trackplan.api.TrackShape
import de.moba.modules.trackplan.api.TrackTile
import de.moba.modules.trackplan.ui.PLAN_VIEW_TAG
import de.moba.modules.trackplan.ui.TrackPlanMode
import de.moba.modules.trackplan.ui.TrackPlanScreen
import de.moba.modules.trackplan.ui.TrackPlanTrains
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
import kotlin.test.assertTrue

/** Bloecke wie in iTrain: Zuege einsetzen, ziehen, entfernen, Bloecke freigeben, Kontextmenue. */
@OptIn(ExperimentalTestApi::class)
class BlocksUiTest {
    private val plan = TrackPlan(
        "bloecke", "Blöcke", 12, 4,
        (0 until 12).map { x ->
            when (x) {
                2 -> TrackTile(x, 1, TrackShape.FEEDBACK, 2, feedbackModule = 1, feedbackContact = 1).withProp(AccessoryProps.NAME, "Gleis 1")
                8 -> TrackTile(x, 1, TrackShape.FEEDBACK, 2, feedbackModule = 1, feedbackContact = 2).withProp(AccessoryProps.NAME, "Gleis 2")
                else -> TrackTile(x, 1, TrackShape.STRAIGHT, 2)
            }
        },
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
        ).also {
            it.layout.update { l -> l.copy(locos = listOf(Loco("l1", "BR 218", 12), Loco("l2", "V 100", 7))) }
            it.connect(StationConfig("simulator")).join()
        }
    }

    private fun MobaApp.plans() = session.value!!.host.service(TrackPlanService::class)!!

    private val screens = listOf(DrivingScreen, TrackPlanScreen)

    @BeforeTest
    @AfterTest
    fun reset() {
        TrackPlanUiState.mode.value = TrackPlanMode.BETRIEB
        TrackPlanUiState.tileSize.value = 24
        TrackPlanUiState.message.value = null
        TrackPlanUiState.contextMenu.value = null
    }

    @Test
    fun contextMenuPutsTrainIntoBlockAndRemovesIt() = runComposeUiTest {
        val app = app()
        setContent { MobaShell(app, screens, initialScreen = TrackPlanScreen.moduleId) }
        val view = onNodeWithTag(PLAN_VIEW_TAG + "bloecke")
        view.performTouchInput { longClick(Offset(2.5f * 24.dp.toPx(), 1.5f * 24.dp.toPx())) }
        onNodeWithText("Zug einsetzen").assertExists()
        // "V 100" steht auch in der Zugleiste; anklickbar ist nur der Menueeintrag
        onNode(hasText("V 100") and hasClickAction()).performClick()
        waitForIdle()
        assertEquals(mapOf((1 to 1) to 7), app.plans().trains.value)

        app.plans().reserve(listOf(1 to 1), BlockReservation("hand", "Test"))
        waitForIdle()
        view.performTouchInput { longClick(Offset(2.5f * 24.dp.toPx(), 1.5f * 24.dp.toPx())) }
        onNodeWithText("Block freigeben").performClick()
        waitForIdle()
        assertTrue(app.plans().reservations.value.isEmpty())

        view.performTouchInput { longClick(Offset(2.5f * 24.dp.toPx(), 1.5f * 24.dp.toPx())) }
        onNodeWithText("Zug entfernen").performClick()
        waitForIdle()
        assertTrue(app.plans().trains.value.isEmpty())
    }

    @Test
    fun trainIsDraggedFromStripIntoPlanAndOnToAnotherBlock() = runComposeUiTest {
        val app = app()
        setContent { MobaShell(app, screens, initialScreen = TrackPlanScreen.moduleId) }
        waitForIdle()
        val chip = onNodeWithText("BR 218").fetchSemanticsNode().boundsInRoot.center
        val grid = onNodeWithTag(PLAN_VIEW_TAG + "bloecke").fetchSemanticsNode().boundsInRoot.topLeft
        val cell = with(density) { 24.dp.toPx() }
        fun block(x: Int) = grid + Offset((x + 0.5f) * cell, 1.5f * cell)
        onRoot().performTouchInput {
            down(chip)
            moveTo(chip + Offset(20f, 20f))
            moveTo(block(2))
            up()
        }
        waitForIdle()
        assertEquals(mapOf((1 to 1) to 12), app.plans().trains.value)

        onRoot().performTouchInput {
            down(block(2))
            moveTo(block(2) + Offset(20f, 0f))
            moveTo(block(8))
            up()
        }
        waitForIdle()
        assertEquals(mapOf((1 to 2) to 12), app.plans().trains.value)
    }

    @Test
    fun trainPositionsAreRemembered() {
        val text = TrackPlanTrains.encode(mapOf((2 to 5) to 7, (1 to 3) to 12))
        assertEquals("1.3=12;2.5=7", text)
        assertEquals(mapOf((1 to 3) to 12, (2 to 5) to 7), TrackPlanTrains.decode(text))
        assertEquals(emptyMap(), TrackPlanTrains.decode("kaputt;1.x=3"))
    }
}
