package de.moba.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import de.moba.adapters.catalog.StationCatalog
import de.moba.core.runtime.LicenseInfo
import de.moba.core.station.StationConfig
import de.moba.modules.driving.DrivingModule
import de.moba.modules.driving.ui.DrivingScreen
import de.moba.modules.trackplan.DemoPlans
import de.moba.modules.trackplan.TrackPlanModule
import de.moba.modules.trackplan.TrackPlanRepository
import de.moba.modules.trackplan.api.TrackPlan
import de.moba.modules.trackplan.api.TrackPlanService
import de.moba.modules.trackplan.api.TrackPlanStore
import de.moba.modules.trackplan.ui.TrackPlanMode
import de.moba.modules.trackplan.ui.TrackPlanScreen
import de.moba.modules.trackplan.ui.TrackPlanUiState
import de.moba.modules.trackplan.ui.TrackPlanWindows
import de.moba.ui.shell.MENU_BAR_TAG
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.MobaShell
import de.moba.ui.shell.ModuleWindowFrame
import de.moba.ui.shell.ShellUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Menueleiste oben mit den Unterpunkten der Reiter; Gleisbilder in eigenen Fenstern (Pro). */
@OptIn(ExperimentalTestApi::class)
class MenuBarAndWindowsTest {
    private fun app(license: LicenseInfo): MobaApp = runBlocking {
        val plans = object : TrackPlanStore {
            override fun load() = listOf(DemoPlans.layout(), DemoPlans.stagingYard(), DemoPlans.industry())
            override fun save(plans: List<TrackPlan>) = Unit
        }
        MobaApp(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            drivers = listOf(StationCatalog.simulator),
            moduleFactory = { listOf(DrivingModule(), TrackPlanModule(TrackPlanRepository(plans))) },
            license = license,
        ).also { it.connect(StationConfig("simulator")).join() }
    }

    private val screens = listOf(DrivingScreen, TrackPlanScreen)

    private fun SemanticsNodeInteractionsProvider.menu(title: String) =
        onNode(hasText(title) and hasAnyAncestor(hasTestTag(MENU_BAR_TAG)))

    @BeforeTest
    @AfterTest
    fun reset() {
        TrackPlanWindows.reset()
        TrackPlanUiState.mode.value = TrackPlanMode.BETRIEB
        ShellUiState.menuBarOpen.value = null
        ShellUiState.separateWindows.value = false
    }

    @Test
    fun menuBarReachesSubItems() = runComposeUiTest {
        val app = app(LicenseInfo.of("basis"))
        setContent { MobaShell(app, screens, menuBar = true) }
        menu("Gleisbild").performClick()
        waitForIdle()
        onNodeWithText("Bearbeiten").performClick()
        waitForIdle()
        assertEquals(TrackPlanMode.BEARBEITEN, TrackPlanUiState.mode.value)
        // Der Reiter ist jetzt offen: Editor mit seinen Werkzeugen
        onNodeWithText("Speichern").assertExists()
    }

    @Test
    fun planOpensInOwnWindowWithPro() = runComposeUiTest {
        val app = app(LicenseInfo.of("pro"))
        setContent { MobaShell(app, screens, menuBar = true, separateWindows = true, initialScreen = TrackPlanScreen.moduleId) }
        menu("Gleisbild").performClick()
        waitForIdle()
        onNodeWithText("Schattenbahnhof in eigenem Fenster").performClick()
        waitForIdle()
        assertEquals(listOf("schattenbahnhof"), TrackPlanWindows.open(app).value)
        val service = app.session.value!!.host.service(TrackPlanService::class)!!
        assertTrue("schattenbahnhof" in service.visiblePlans.value)
        // Im Hauptfenster traegt sein Schalter jetzt das Fenster-Zeichen
        onNodeWithContentDescription("In eigenem Fenster").assertExists()
    }

    @Test
    fun windowGoesBackToMainWindow() = runComposeUiTest {
        val app = app(LicenseInfo.of("pro"))
        ShellUiState.separateWindows.value = true
        assertEquals(null, TrackPlanWindows.detach(app, "industrie"))
        setContent {
            ModuleWindowFrame(app, "Industrie", fullscreen = false, onToggleFullscreen = {}, onClose = { TrackPlanWindows.close(app, "industrie") }) {
                TrackPlanWindows.Content(app, "industrie")
            }
        }
        waitForIdle()
        onNodeWithText("NOTHALT").assertExists()
        onNodeWithText("Ins Hauptfenster").performClick()
        waitForIdle()
        assertFalse("industrie" in TrackPlanWindows.open(app).value)
        ShellUiState.separateWindows.value = false
    }

    @Test
    fun withoutProNoOwnWindows() {
        val app = app(LicenseInfo.of("basis"))
        ShellUiState.separateWindows.value = true
        assertTrue(TrackPlanWindows.detach(app, "industrie")!!.contains("Pro"))
        assertTrue(TrackPlanWindows.open(app).value.isEmpty())
        ShellUiState.separateWindows.value = false
    }
}
