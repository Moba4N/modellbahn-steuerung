package de.moba.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import de.moba.adapters.catalog.StationCatalog
import de.moba.core.runtime.LicenseInfo
import de.moba.core.station.StationConfig
import de.moba.modules.actions.ActionModule
import de.moba.modules.actions.ActionRepository
import de.moba.modules.actions.ui.ActionsEditor
import de.moba.modules.driving.DrivingModule
import de.moba.modules.driving.ui.DrivingScreen
import de.moba.modules.routes.RouteModule
import de.moba.modules.routes.RouteRepository
import de.moba.modules.routes.ui.RouteEditors
import de.moba.modules.trackplan.TrackPlanModule
import de.moba.modules.trackplan.TrackPlanRepository
import de.moba.modules.trackplan.api.TrackPlanService
import de.moba.modules.trackplan.api.block
import de.moba.modules.trackplan.api.blockName
import de.moba.modules.trackplan.ui.TrackObjectEditors
import de.moba.modules.vehicles.VehicleModule
import de.moba.modules.vehicles.VehicleRepository
import de.moba.modules.vehicles.api.VehicleService
import de.moba.modules.vehicles.ui.VehicleEditors
import de.moba.ui.shell.EditUiState
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

/** Menue "Bearbeiten": alle Objektlisten erreichbar, Zuggattung und Bahnhof anlegen ohne Gleisbild. */
@OptIn(ExperimentalTestApi::class)
class EditMenuTest {
    private val editors = VehicleEditors + RouteEditors + TrackObjectEditors + ActionsEditor

    private fun app(): MobaApp = runBlocking {
        MobaApp(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            drivers = listOf(StationCatalog.simulator),
            moduleFactory = {
                listOf(
                    DrivingModule(), TrackPlanModule(TrackPlanRepository(null)), VehicleModule(VehicleRepository(null)),
                    RouteModule(RouteRepository(null)), ActionModule(ActionRepository(null)),
                )
            },
            license = LicenseInfo.of("basis", "pro"),
        ).also { it.connect(StationConfig("simulator")).join() }
    }

    @BeforeTest
    @AfterTest
    fun reset() {
        EditUiState.menuOpen.value = false
        EditUiState.open.value = null
        EditUiState.selection.value = emptyMap()
    }

    @Test
    fun allListsAreInTheMenuAndObjectsCanBeCreated() = runComposeUiTest {
        val app = app()
        val host = app.session.value!!.host
        setContent { MobaShell(app, listOf(DrivingScreen), editors = editors) }
        onNode(hasText("Bearbeiten") and hasClickAction()).performClick()
        for (title in listOf(
            "Schnittstellen", "Lokomotiven", "Wagen", "Zuggattungen", "Züge", "Zugrouten", "Rückmelder", "Zubehör",
            "Fahrwege", "Aktionen", "Blöcke", "Bahnhöfe", "Booster",
        )) {
            onNodeWithText("$title …").assertExists()
        }
        onNodeWithText("Zuggattungen …").performClick()
        onNodeWithText("Neue Zuggattung").performClick()
        onAllNodes(hasSetTextAction())[0].performTextInput("RE")
        onAllNodes(hasSetTextAction())[1].performTextInput("Regional-Express")
        onNodeWithText("Speichern").performScrollTo().performClick()
        waitForIdle()
        val category = host.service(VehicleService::class)!!.fleet.value.categories.single()
        assertEquals("RE", category.code)

        // Links in der Liste zu den Bahnhoefen wechseln und einen mit einem Block anlegen
        onNode(hasText("Bahnhöfe") and hasClickAction()).performClick()
        onNodeWithText("Neuer Bahnhof").performClick()
        onAllNodes(hasSetTextAction())[0].performTextInput("Neustadt")
        val plans = host.service(TrackPlanService::class)!!
        val block = plans.plans.value.flatMap { p -> p.tiles.mapNotNull { it.block() } }.first()
        onAllNodes(hasText(plans.plans.value.blockName(block)) and hasClickAction())[0].performScrollTo().performClick()
        onNodeWithText("Speichern").performScrollTo().performClick()
        waitForIdle()
        val station = plans.objects.value.stations.single()
        assertEquals("Neustadt", station.name)
        assertEquals(listOf(block), station.blocks)

        // Der Block zeigt seinen Bahnhof
        onNode(hasText("Blöcke") and hasClickAction()).performClick()
        onAllNodes(hasText(plans.plans.value.blockName(block)) and hasClickAction())[0].performClick()
        onNodeWithText("Bahnhof Neustadt").assertExists()
    }
}
