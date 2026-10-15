package de.moba.modules.routes.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.moba.modules.routes.api.Feedback
import de.moba.modules.routes.api.Route
import de.moba.modules.routes.api.RouteService
import de.moba.modules.routes.api.RouteSetting
import de.moba.modules.trackplan.api.Cell
import de.moba.modules.trackplan.api.TileKind
import de.moba.modules.trackplan.api.TrackPath
import de.moba.modules.trackplan.api.TrackPlan
import de.moba.modules.trackplan.api.TrackPlanService
import de.moba.modules.trackplan.api.TrackRouting
import de.moba.modules.trackplan.ui.TrackPlanPicker
import de.moba.ui.shell.MobaApp

const val ROUTES_PLAN_TAG = "fahrstrassen-gleisbild"

/** Kachelgroesse des Gleisbilds im Editor. */
const val ROUTES_PLAN_CELL = 22

/** Stand der Auswahl im Gleisbild fuer die Fahrstrasse [routeId]. */
data class RoutePick(
    val routeId: String = "",
    val open: Boolean = false,
    val planId: String = "",
    val from: Cell? = null,
    val to: Cell? = null,
    val choice: Int = 0,
)

/** Was aus dem Gleisbild fuer eine Fahrstrasse uebernommen wird. */
internal class PickedRoute(
    val name: String,
    val settings: List<RouteSetting>,
    val start: Feedback?,
    val end: Feedback?,
    val planId: String,
    val path: List<Pair<Int, Int>>,
)

@Composable
private fun trackPlanService(app: MobaApp): TrackPlanService? {
    val session by app.session.collectAsState()
    return session?.host?.service(TrackPlanService::class)
}

/** Kurzname einer Kachel fuer Fahrstrassennamen. */
internal fun TrackPlan.label(cell: Cell): String {
    val tile = tileAt(cell.x, cell.y) ?: return "Feld ${cell.x},${cell.y}"
    return when {
        tile.text.isNotBlank() -> tile.text
        tile.shape.kind == TileKind.FEEDBACK && tile.feedbackModule != null -> "Melder ${tile.feedbackModule}.${tile.feedbackContact}"
        tile.shape.kind == TileKind.SIGNAL && tile.address != null -> "Signal ${tile.address}"
        tile.shape.kind == TileKind.BUFFER -> "Prellbock ${cell.x},${cell.y}"
        else -> "Feld ${cell.x},${cell.y}"
    }
}

private fun TrackPlan.feedbackAt(cell: Cell): Feedback? {
    val tile = tileAt(cell.x, cell.y) ?: return null
    if (tile.shape.kind != TileKind.FEEDBACK) return null
    return Feedback(tile.feedbackModule ?: return null, tile.feedbackContact ?: return null)
}

private fun Pair<Int, Int>.feedback() = Feedback(first, second)

/** Weg in eine Fahrstrasse umsetzen: Start = erster, Ziel = letzter Rueckmelder auf dem Weg. */
private fun TrackPlan.pick(path: TrackPath, from: Cell, to: Cell): PickedRoute {
    val feedbacks = path.feedbacks.map { it.feedback() }
    return PickedRoute(
        name = "${label(from)} → ${label(to)}",
        settings = path.settings.distinctBy { it.first }.map { RouteSetting(it.first, it.second) },
        start = feedbacks.firstOrNull()?.takeIf { feedbacks.size > 1 },
        end = feedbacks.lastOrNull(),
        planId = id,
        path = path.cells.map { it.x to it.y },
    )
}

private fun TrackPath.summary(index: Int): String {
    val turnouts = turnoutCount
    return "Weg ${index + 1}: ${cells.size} Felder, " + if (turnouts == 1) "1 Weiche" else "$turnouts Weichen"
}

/** Gleisbild mit Auswahl, falls es mehrere gibt; scrollt bei grossen Plaenen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanArea(
    plans: List<TrackPlan>,
    plan: TrackPlan,
    onPlan: (String) -> Unit,
    highlight: List<Cell>,
    marks: Map<Cell, String>,
    turnouts: Map<Int, Boolean>,
    onTap: (Cell) -> Unit,
) {
    if (plans.size > 1) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            plans.forEach { p -> FilterChip(selected = p.id == plan.id, onClick = { onPlan(p.id) }, label = { Text(p.name) }) }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(4.dp)
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState()),
    ) {
        TrackPlanPicker(plan, ROUTES_PLAN_CELL.dp, turnouts, highlight, marks, Modifier.testTag(ROUTES_PLAN_TAG), onTap)
    }
}

@Composable
private fun OpenButton(open: Boolean, onToggle: () -> Unit) {
    OutlinedButton(onClick = onToggle) {
        Icon(Icons.Outlined.Map, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (open) "Gleisbild ausblenden" else "Im Gleisbild wählen")
    }
}

// ------------------------------------------------------------------ Fahrstrasse

/** Start und Ziel im Gleisbild antippen; die App sucht die Wege und setzt Weichen und Melder ein. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RouteFromPlan(app: MobaApp, route: Route, onApply: (PickedRoute) -> Unit) {
    val service = trackPlanService(app) ?: return
    val plans by service.plans.collectAsState()
    if (plans.isEmpty()) return
    val pick = RoutesUiState.routePick
    val state by pick.collectAsState()
    if (state.routeId != route.id) {
        // Neue Fahrstrasse im Editor: Auswahl mit dem gespeicherten Weg beginnen
        pick.value = RoutePick(
            route.id,
            open = false,
            planId = route.planId,
            from = route.path.firstOrNull()?.let { Cell(it.first, it.second) },
            to = route.path.lastOrNull()?.let { Cell(it.first, it.second) },
        )
    }
    val open = state.open
    val planId = state.planId.takeIf { id -> plans.any { it.id == id } } ?: plans.first().id
    val from = state.from
    val to = state.to
    val choice = state.choice
    val plan = plans.firstOrNull { it.id == planId } ?: plans.first()
    val paths = remember(plan, from, to) {
        val a = from
        val b = to
        if (a != null && b != null) TrackRouting.findPaths(plan, a, b) else emptyList()
    }
    val path = paths.getOrNull(choice)

    OpenButton(open) { pick.value = state.copy(open = !open) }
    if (!open) return
    Hint(
        when {
            from == null -> "Tippe auf das Gleis, an dem die Fahrstraße beginnt."
            to == null -> "Jetzt auf das Ziel tippen."
            else -> "Erneut tippen beginnt eine neue Auswahl."
        },
    )
    PlanArea(
        plans, plan,
        onPlan = { pick.value = state.copy(planId = it, from = null, to = null, choice = 0) },
        highlight = path?.cells ?: listOfNotNull(from),
        marks = listOfNotNull(from?.let { it to "A" }, to?.let { it to "B" }).toMap(),
        turnouts = path?.settings?.toMap().orEmpty(),
    ) { cell ->
        // aktuellen Stand lesen, nicht den beim Zeichnen gemerkten
        val now = pick.value
        pick.value = when {
            now.from == null || now.to != null -> now.copy(planId = planId, from = cell, to = null, choice = 0)
            cell != now.from -> now.copy(planId = planId, to = cell, choice = 0)
            else -> now
        }
    }
    if (from != null && to != null) {
        if (paths.isEmpty()) {
            Error("Keine Verbindung von „${plan.label(from)}“ nach „${plan.label(to)}“ gefunden.")
        } else {
            if (paths.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    paths.forEachIndexed { i, p -> FilterChip(selected = i == choice, onClick = { pick.value = state.copy(choice = i) }, label = { Text(p.summary(i)) }) }
                }
            }
            path!!
            if (path.missingAddresses.isNotEmpty()) {
                Error("${path.missingAddresses.size} Weiche(n) auf dem Weg haben noch keine Adresse. Bitte im Gleisbild-Editor nachtragen.")
            }
            if (path.feedbacks.isEmpty()) Hint("Auf dem Weg liegt kein Rückmelder; die Fahrstraße löst sich dann nicht von selbst auf.")
            Button(onClick = {
                onApply(plan.pick(path, from, to))
                pick.value = state.copy(open = false)
            }) { Text("Weg übernehmen") }
        }
    }
}

// ------------------------------------------------------------------ Pendelstrecke

/** Was aus dem Gleisbild fuer eine Pendelstrecke uebernommen wird. */
internal class PickedShuttle(val name: String, val endA: Feedback, val endB: Feedback, val brakeA: Feedback?, val brakeB: Feedback?)

/** Die beiden Endmelder antippen; Bremsmelder sind die Melder direkt davor auf dem Weg. */
@Composable
internal fun ShuttleFromPlan(app: MobaApp, key: String, onApply: (PickedShuttle) -> Unit) {
    val service = trackPlanService(app) ?: return
    val plans by service.plans.collectAsState()
    if (plans.isEmpty()) return
    var open by remember(key) { mutableStateOf(false) }
    var planId by remember(key) { mutableStateOf(plans.first().id) }
    var a by remember(key) { mutableStateOf<Cell?>(null) }
    var b by remember(key) { mutableStateOf<Cell?>(null) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    val plan = plans.firstOrNull { it.id == planId } ?: plans.first()
    val path = remember(plan, a, b) {
        val x = a
        val y = b
        if (x != null && y != null) TrackRouting.findPaths(plan, x, y, max = 1).firstOrNull() else null
    }

    OpenButton(open) { open = !open }
    if (!open) return
    Hint(if (a == null) "Tippe auf den Rückmelder an Ende A." else if (b == null) "Jetzt den Rückmelder an Ende B antippen." else "Erneut tippen beginnt neu.")
    PlanArea(
        plans, plan,
        onPlan = {
            planId = it
            a = null
            b = null
        },
        highlight = path?.cells ?: listOfNotNull(a),
        marks = listOfNotNull(a?.let { it to "A" }, b?.let { it to "B" }).toMap(),
        turnouts = path?.settings?.toMap().orEmpty(),
    ) { cell ->
        if (plan.feedbackAt(cell) == null) {
            error = "Bitte einen Rückmelder antippen (im Gleisbild mit Modul und Kontakt eingetragen)."
            return@PlanArea
        }
        error = null
        if (a == null || b != null) {
            a = cell
            b = null
        } else if (cell != a) {
            b = cell
        }
    }
    error?.let { Error(it) }
    val x = a
    val y = b
    if (x != null && y != null) {
        if (path == null) {
            Error("Keine Verbindung zwischen den beiden Meldern gefunden.")
            return
        }
        if (path.turnoutCount > 0) {
            Hint("Der Weg führt über ${path.turnoutCount} Weiche(n). Die Pendelstrecke stellt sie nicht; dafür vorher eine Fahrstraße stellen.")
        }
        val fbs = path.feedbacks.map { it.feedback() }
        Button(onClick = {
            onApply(
                PickedShuttle(
                    "${plan.label(x)} ↔ ${plan.label(y)}",
                    plan.feedbackAt(x)!!, plan.feedbackAt(y)!!,
                    brakeA = fbs.getOrNull(1)?.takeIf { fbs.size >= 4 },
                    brakeB = fbs.getOrNull(fbs.size - 2)?.takeIf { fbs.size >= 4 },
                ),
            )
            open = false
        }) { Text("Melder übernehmen") }
    }
}

// ------------------------------------------------------------------ Zugroute

/**
 * Halte nacheinander antippen. Fuer jeden Abschnitt nimmt die App eine vorhandene Fahrstrasse mit genau diesem Weg
 * oder legt eine neue an. [onApply] bekommt die Fahrstrassen-Ids in Reihenfolge.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TrainRouteFromPlan(app: MobaApp, routes: RouteService, key: String, onApply: (List<String>) -> Unit) {
    val service = trackPlanService(app) ?: return
    val plans by service.plans.collectAsState()
    if (plans.isEmpty()) return
    var open by remember(key) { mutableStateOf(false) }
    var planId by remember(key) { mutableStateOf(plans.first().id) }
    val stops = remember(key) { mutableStateListOf<Cell>() }
    var error by remember(key) { mutableStateOf<String?>(null) }
    val plan = plans.firstOrNull { it.id == planId } ?: plans.first()
    val legs = remember(plan, stops.toList()) {
        stops.zipWithNext { s, t -> TrackRouting.findPaths(plan, s, t, max = 1).firstOrNull() }
    }

    OpenButton(open) { open = !open }
    if (!open) return
    Hint("Tippe die Halte nacheinander an, jeweils einen Rückmelder. Jeder Abschnitt wird eine Fahrstraße.")
    PlanArea(
        plans, plan,
        onPlan = {
            planId = it
            stops.clear()
        },
        highlight = legs.filterNotNull().flatMap { it.cells }.ifEmpty { stops.toList() },
        marks = stops.mapIndexed { i, c -> c to "${i + 1}" }.toMap(),
        turnouts = legs.filterNotNull().flatMap { it.settings }.toMap(),
    ) { cell ->
        if (plan.feedbackAt(cell) == null) {
            error = "Bitte einen Rückmelder antippen."
            return@PlanArea
        }
        error = null
        if (stops.lastOrNull() != cell) stops += cell
    }
    error?.let { Error(it) }
    legs.forEachIndexed { i, leg ->
        val text = "${i + 1} → ${i + 2}: ${plan.label(stops[i])} → ${plan.label(stops[i + 1])}"
        if (leg == null) Error("$text: keine Verbindung") else Hint("$text (${leg.summary(0).substringAfter(": ")})")
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (stops.isNotEmpty()) {
            TextButton(onClick = { stops.removeAt(stops.lastIndex) }) {
                Icon(Icons.Outlined.Undo, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Letzten Halt entfernen")
            }
        }
        if (legs.isNotEmpty() && legs.all { it != null }) {
            Button(onClick = {
                val ids = legs.mapIndexed { i, leg ->
                    val picked = plan.pick(leg!!, stops[i], stops[i + 1])
                    routes.book.value.routes.firstOrNull { it.planId == picked.planId && it.path == picked.path }?.id
                        ?: routes.newId(RoutesTab.ROUTES.prefix).also { id ->
                            routes.saveRoute(Route(id, picked.name, picked.settings, picked.start, picked.end, picked.planId, picked.path))
                        }
                }
                onApply(ids)
                stops.clear()
                open = false
            }) { Text("Abschnitte übernehmen") }
        }
    }
}
