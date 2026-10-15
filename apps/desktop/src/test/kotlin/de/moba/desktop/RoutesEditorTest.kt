package de.moba.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import de.moba.adapters.catalog.StationCatalog
import de.moba.adapters.simulator.SimulatorStation
import de.moba.core.runtime.LicenseInfo
import de.moba.core.station.StationConfig
import de.moba.modules.routes.RouteModule
import de.moba.modules.routes.RouteRepository
import de.moba.modules.routes.api.Feedback
import de.moba.modules.routes.api.RouteBook
import de.moba.modules.routes.api.RouteService
import de.moba.modules.routes.api.RouteSetting
import de.moba.modules.routes.api.RouteStore
import de.moba.modules.routes.ui.RoutesScreen
import de.moba.modules.routes.ui.RoutesTab
import de.moba.modules.routes.ui.RoutesUiState
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.MobaShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Legt eine Fahrstrasse ueber die Oberflaeche an, speichert sie und stellt sie. */
@OptIn(ExperimentalTestApi::class)
class RoutesEditorTest {
    private class MemoryStore : RouteStore {
        var saved: RouteBook? = null
        override fun load() = saved
        override fun save(book: RouteBook) {
            saved = book
        }
    }

    @AfterTest
    fun reset() {
        RoutesUiState.tab.value = RoutesTab.ROUTES
        RoutesUiState.select(null)
    }

    @Test
    fun createSaveAndSetARoute() = runComposeUiTest {
        val store = MemoryStore()
        val app = runBlocking {
            MobaApp(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
                drivers = listOf(StationCatalog.simulator),
                moduleFactory = { listOf(RouteModule(RouteRepository(store))) },
                license = LicenseInfo.of("basis"),
            ).also { it.connect(StationConfig("simulator")).join() }
        }
        val service = app.session.value!!.host.service(RouteService::class)!!
        setContent { MobaShell(app, listOf(RoutesScreen), emptyList(), initialScreen = RoutesScreen.moduleId) }

        onNodeWithText("Neu").performClick()
        waitForIdle()
        onNode(hasSetTextAction() and hasText("Name")).performTextInput("Einfahrt Gleis 3")
        onNodeWithText("Weiche oder Signal hinzufügen").performClick()
        waitForIdle()
        onNode(hasSetTextAction() and hasText("Adresse")).performTextInput("7")
        onNodeWithText("Abzweig").performClick()
        onNode(hasSetTextAction() and hasText("Ziel: löst auf")).performTextInput("1.4")
        onNodeWithText("Speichern").performClick()
        waitForIdle()

        val route = store.saved!!.routes.single()
        assertEquals("Einfahrt Gleis 3", route.name)
        assertEquals(listOf(RouteSetting(7, true)), route.settings)
        assertEquals(Feedback(1, 4), route.end)

        onNodeWithText("Stellen").performClick()
        waitUntil(timeoutMillis = 3_000) { route.id in service.activeRoutes.value }
        assertTrue((app.session.value!!.station as SimulatorStation).turnout(7))
    }
}
