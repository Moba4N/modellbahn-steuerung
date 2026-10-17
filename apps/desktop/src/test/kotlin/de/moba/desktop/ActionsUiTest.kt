package de.moba.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import de.moba.adapters.catalog.StationCatalog
import de.moba.adapters.simulator.SimulatorStation
import de.moba.core.runtime.LicenseInfo
import de.moba.core.station.StationConfig
import de.moba.modules.actions.ActionModule
import de.moba.modules.actions.ActionRepository
import de.moba.modules.actions.api.ActionRule
import de.moba.modules.actions.api.ActionService
import de.moba.modules.actions.api.Command
import de.moba.modules.actions.api.CommandType
import de.moba.modules.actions.api.Condition
import de.moba.modules.actions.api.ConditionType
import de.moba.modules.actions.ui.ActionsMenu
import de.moba.modules.actions.ui.ActionsUiState
import de.moba.modules.driving.DrivingModule
import de.moba.modules.driving.ui.DrivingScreen
import de.moba.modules.trackplan.TrackPlanModule
import de.moba.modules.trackplan.TrackPlanRepository
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

/** Burger-Menue "Aktionen": Aktion mit Bedingung und Befehl anlegen, im Betrieb ausloesen, ein- und ausschalten. */
@OptIn(ExperimentalTestApi::class)
class ActionsUiTest {
    private fun app(): MobaApp = runBlocking {
        MobaApp(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            drivers = listOf(StationCatalog.simulator),
            moduleFactory = { listOf(DrivingModule(), TrackPlanModule(TrackPlanRepository(null)), ActionModule(ActionRepository(null))) },
            license = LicenseInfo.of("basis", "pro"),
        ).also { it.connect(StationConfig("simulator")).join() }
    }

    private fun MobaApp.actions() = session.value!!.host.service(ActionService::class)!!
    private fun MobaApp.sim() = session.value!!.station as SimulatorStation

    @BeforeTest
    @AfterTest
    fun reset() {
        ActionsUiState.menuOpen.value = false
        ActionsUiState.window.value = null
    }

    @Test
    fun newActionIsBuiltInTheEditorAndFires() = runComposeUiTest {
        val app = app()
        setContent { MobaShell(app, listOf(DrivingScreen), menus = listOf(ActionsMenu)) }
        onNode(hasText("Aktionen") and hasClickAction()).performClick()
        onNodeWithText("Noch keine Aktion.").assertExists()
        onNodeWithText("Neue Aktion …").performClick()

        onAllNodes(hasSetTextAction())[0].performTextInput("Einfahrt Nord")
        onNodeWithText("Bedingung hinzufügen").performScrollTo().performClick()
        onNodeWithText("Block belegt").performClick()
        onAllNodes(hasSetTextAction())[1].performTextInput("1.3")
        onNodeWithText("Befehl hinzufügen").performScrollTo().performClick()
        onNodeWithText("Zubehör schalten").performClick()
        onAllNodes(hasSetTextAction())[2].performTextInput("7")
        onNodeWithText("Stellung: gerade / grün").performScrollTo().performClick()
        onNodeWithText("abzweigend / rot").performClick()
        onNodeWithText("Speichern").performScrollTo().performClick()
        waitForIdle()

        val rule = app.actions().book.value.rules.single()
        assertEquals("Einfahrt Nord", rule.name)
        assertEquals(Condition.and(Condition.of(ConditionType.BLOCK_OCCUPIED, "block" to "1.3")), rule.condition)
        assertEquals(listOf(Command.of(CommandType.ACCESSORY, "adresse" to "7", "stellung" to "abzweigend")), rule.commands)
        onNodeWithText("Bedingung nicht erfüllt").assertExists()

        runBlocking { app.sim().simulateFeedback(1, 3, occupied = true) }
        waitUntil(timeoutMillis = 5000) { app.sim().turnout(7) }
        waitUntil(timeoutMillis = 5000) { onAllNodesWithText("Bedingung erfüllt").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun menuSwitchesAndRunsActions() = runComposeUiTest {
        val app = app()
        app.actions().save(
            ActionRule("ak-1", "Signal stellen", condition = Condition.of(ConditionType.FEEDBACK, "melder" to "9.9"), commands = listOf(
                Command.of(CommandType.ACCESSORY, "adresse" to "9", "stellung" to "abzweigend"),
            )),
        )
        setContent { MobaShell(app, listOf(DrivingScreen), menus = listOf(ActionsMenu)) }
        ActionsUiState.menuOpen.value = true
        onNodeWithText("Signal stellen").assertExists()
        onNodeWithText("Aktionen (1 von 1 aktiv)").assertExists()
        // Ausschalten ueber den Schalter, dann von Hand starten
        onNodeWithTag("aktion-an-ak-1").performClick()
        waitUntil(timeoutMillis = 5000) { !app.actions().book.value.rules.single().enabled }
        runBlocking { app.sim().setTurnout(9, false) }
        onNode(hasContentDescription("„Signal stellen“ ausführen")).performClick()
        waitUntil(timeoutMillis = 5000) { app.sim().turnout(9) }
        assertTrue(app.actions().log.value.any { "von Hand" in it.text })
    }
}
