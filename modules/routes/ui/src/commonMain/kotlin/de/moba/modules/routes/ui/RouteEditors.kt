package de.moba.modules.routes.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.Route
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import de.moba.modules.routes.api.ROUTES_MODULE_ID
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.ModuleEditor

/** Wechselt beim Oeffnen auf [tab] (Auswahl zuruecksetzen, wenn es ein anderer war) und zeigt den Reiter Fahrstrassen. */
@Composable
private fun RoutesOn(app: MobaApp, tab: RoutesTab, also: Set<RoutesTab> = emptySet()) {
    LaunchedEffect(Unit) {
        if (RoutesUiState.tab.value != tab && RoutesUiState.tab.value !in also) {
            RoutesUiState.tab.value = tab
            RoutesUiState.select(null)
        }
    }
    RoutesScreen.Content(app)
}

/** Bearbeiten > Zugrouten (und Pendelstrecken). */
object TrainRoutesEditor : ModuleEditor {
    override val moduleId = ROUTES_MODULE_ID
    override val key = "zugrouten"
    override val title = "Zugrouten"
    override val icon = Icons.Outlined.Route
    override val order = 50

    @Composable
    override fun Content(app: MobaApp) = RoutesOn(app, RoutesTab.TRAINS, setOf(RoutesTab.SHUTTLES))
}

/** Bearbeiten > Fahrwege: die Fahrstrassen mit ihren Weichen und Meldern. */
object RoutePathsEditor : ModuleEditor {
    override val moduleId = ROUTES_MODULE_ID
    override val key = "fahrwege"
    override val title = "Fahrwege"
    override val icon = Icons.Outlined.AltRoute
    override val order = 80

    @Composable
    override fun Content(app: MobaApp) = RoutesOn(app, RoutesTab.ROUTES)
}

val RouteEditors = listOf(TrainRoutesEditor, RoutePathsEditor)
