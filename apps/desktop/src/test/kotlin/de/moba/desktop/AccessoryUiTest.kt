package de.moba.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import de.moba.adapters.catalog.StationCatalog
import de.moba.core.runtime.LicenseInfo
import de.moba.core.station.StationConfig
import de.moba.modules.driving.DrivingModule
import de.moba.modules.driving.ui.DrivingScreen
import de.moba.modules.trackplan.TrackPlanModule
import de.moba.modules.trackplan.TrackPlanRepository
import de.moba.modules.trackplan.api.AccessoryProps
import de.moba.modules.trackplan.api.TrackPlan
import de.moba.modules.trackplan.api.TrackPlanService
import de.moba.modules.trackplan.api.TrackPlanStore
import de.moba.modules.trackplan.api.TrackShape
import de.moba.modules.trackplan.api.TrackTile
import de.moba.modules.trackplan.ui.EditTool
import de.moba.modules.trackplan.ui.TrackPlanMode
import de.moba.modules.trackplan.ui.TrackPlanScreen
import de.moba.modules.trackplan.ui.TrackPlanUiState
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.MobaShell
import de.moba.ui.shell.SoundSupport
import de.moba.modules.trackplan.ui.TrackPlanSounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Zubehoer wie in iTrain: Reiter im Editor, Drehscheibe steuern, Sound abspielen. */
@OptIn(ExperimentalTestApi::class)
class AccessoryUiTest {
    private val plan = TrackPlan(
        "zubehoer", "Zubehör", 20, 12,
        listOf(
            TrackTile(6, 6, TrackShape.TURNTABLE, address = 225).withProp(AccessoryProps.TRACKS, "0, 6, 12"),
            TrackTile(1, 1, TrackShape.LEVEL_CROSSING, 2, address = 30),
            TrackTile(3, 1, TrackShape.SOUND).withProp(AccessoryProps.FILE, "pfiff.wav"),
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

    private val screens = listOf(DrivingScreen, TrackPlanScreen)

    @BeforeTest
    @AfterTest
    fun reset() {
        TrackPlanUiState.mode.value = TrackPlanMode.BETRIEB
        TrackPlanUiState.selected.value = null
        TrackPlanUiState.accessoryTab.value = 0
        TrackPlanUiState.openTable.value = null
        TrackPlanUiState.editPlan.value = null
    }

    @Test
    fun crossingGetsFeedbackInControlTab() = runComposeUiTest {
        val app = app()
        TrackPlanUiState.mode.value = TrackPlanMode.BEARBEITEN
        TrackPlanUiState.tool.value = EditTool.Select
        TrackPlanUiState.selected.value = 1 to 1
        setContent { MobaShell(app, screens, initialScreen = TrackPlanScreen.moduleId) }
        onNodeWithText("Steuerung").performClick()
        onNode(hasSetTextAction() and hasText("Einschalt-Melder")).performTextInput("1.3, 1.4")
        waitForIdle()
        val service = app.session.value!!.host.service(TrackPlanService::class)!!
        assertEquals("1.3, 1.4", service.plans.value.single().tileAt(1, 1)!!.prop(AccessoryProps.ON_FEEDBACK))
        onNodeWithText("Melder 1.3, 1.4").assertExists()
    }

    @Test
    fun turntableDialogTurnsToTrack() = runComposeUiTest {
        val app = app()
        TrackPlanUiState.openTable.value = "zubehoer" to (6 to 6)
        setContent { MobaShell(app, screens, initialScreen = TrackPlanScreen.moduleId) }
        onNodeWithText("Gleis 6").performClick()
        waitUntil(timeoutMillis = 3000) { app.session.value!!.host.service(TrackPlanService::class)!!.tablePositions.value[225] == 6 }
        onNodeWithText("Steht an Gleis 6").assertExists()
    }

    @Test
    fun soundPlaysEvenOnAnotherScreen() = runComposeUiTest {
        val app = app()
        val played = mutableListOf<String>()
        val sound = object : SoundSupport {
            override val formats = "WAV."
            override fun pick(onResult: (String?) -> Unit) = onResult(null)
            override fun play(name: String) {
                played += name
            }
        }
        setContent { MobaShell(app, screens, initialScreen = DrivingScreen.moduleId, sound = sound, effects = listOf(TrackPlanSounds)) }
        waitForIdle()
        val service = app.session.value!!.host.service(TrackPlanService::class)!!
        runBlocking { service.operate(plan.tileAt(3, 1)!!) }
        waitUntil(timeoutMillis = 3000) { played == listOf("pfiff.wav") }
    }
}
