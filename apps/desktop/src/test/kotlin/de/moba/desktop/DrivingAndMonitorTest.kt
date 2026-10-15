package de.moba.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import de.moba.adapters.catalog.StationCatalog
import de.moba.core.model.Loco
import de.moba.core.runtime.LicenseInfo
import de.moba.core.station.StationConfig
import de.moba.modules.driving.DrivingModule
import de.moba.modules.driving.ui.DrivingPanel
import de.moba.modules.driving.ui.DrivingScreen
import de.moba.modules.driving.ui.DrivingUiState
import de.moba.modules.monitor.MonitorModule
import de.moba.modules.monitor.api.MONITOR_MODULE_ID
import de.moba.modules.monitor.ui.MonitorTool
import de.moba.ui.shell.FunctionKeysState
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.MobaShell
import de.moba.ui.shell.ShellUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Fahrpult oeffnen und wieder schliessen, Funktionen bis F31, Monitor ueber das Burger-Menue. */
@OptIn(ExperimentalTestApi::class)
class DrivingAndMonitorTest {
    private fun app(): MobaApp = runBlocking {
        MobaApp(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            drivers = listOf(StationCatalog.simulator),
            moduleFactory = { listOf(DrivingModule(), MonitorModule()) },
            license = LicenseInfo.of("basis"),
        ).also {
            it.connect(StationConfig("simulator")).join()
            it.layout.update { l -> l.copy(locos = listOf(Loco("br218", "BR 218", 3))) }
        }
    }

    @AfterTest
    fun reset() {
        DrivingUiState.selected.value = null
        ShellUiState.openTool.value = null
        ShellUiState.menuOpen.value = false
        FunctionKeysState.reset()
    }

    @Test
    fun throttleClosesAgain() = runComposeUiTest {
        val app = app()
        setContent { MobaShell(app, listOf(DrivingScreen), listOf(DrivingPanel), initialScreen = DrivingScreen.moduleId) }
        onNodeWithText("BR 218").performClick()
        waitForIdle()
        assertEquals(3, DrivingUiState.selected.value)
        onNodeWithContentDescription("Fahrpult schließen").performClick()
        waitForIdle()
        assertNull(DrivingUiState.selected.value)
        // Lok antippen oeffnet, die gewaehlte Lok nochmal antippen schliesst
        onNodeWithText("BR 218").performClick()
        waitForIdle()
        assertEquals(3, DrivingUiState.selected.value)
        onAllNodesWithText("BR 218")[1].performClick()
        waitForIdle()
        assertNull(DrivingUiState.selected.value)
    }

    @Test
    fun functionsUpToF31() = runComposeUiTest {
        val app = app()
        DrivingUiState.selected.value = 3
        setContent { MobaShell(app, listOf(DrivingScreen), listOf(DrivingPanel), initialScreen = DrivingScreen.moduleId) }
        onNodeWithText("F0–F31").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("F31").performScrollTo().performClick()
        waitUntil(timeoutMillis = 3_000) { 31 in (app.session.value!!.station as de.moba.adapters.simulator.SimulatorStation).loco(3).functions }
    }

    @Test
    fun monitorOpensFromTheMenu() = runComposeUiTest {
        val app = app()
        setContent { MobaShell(app, listOf(DrivingScreen), listOf(DrivingPanel), tools = listOf(MonitorTool)) }
        onNodeWithContentDescription("Menü").performClick()
        waitForIdle()
        onNodeWithText("Monitor …").performClick()
        waitForIdle()
        assertEquals(MONITOR_MODULE_ID, ShellUiState.openTool.value)
        onNodeWithText("Anhalten").assertExists()
    }
}
