package de.moba.modules.trackplan.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.moba.modules.symbols.api.SymbolService
import de.moba.modules.trackplan.api.LicenseRequiredException
import de.moba.modules.trackplan.api.TRACKPLAN_MODULE_ID
import de.moba.modules.trackplan.api.TrackPlanService
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.ModuleWindows
import de.moba.ui.shell.ShellUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Gleisbilder in eigenen Fenstern, z. B. Bahnhof, Schattenbahnhof und Industrie auf drei Bildschirmen.
 * Nur mit Pro und nur dort, wo die App eigene Fenster oeffnen kann (Windows). Welche Fenster offen
 * waren, merkt sich die App und oeffnet sie beim naechsten Start wieder.
 */
object TrackPlanWindows : ModuleWindows {
    /** So viele Gleisbilder zeigt Pro hoechstens gleichzeitig, also auch hoechstens so viele Fenster. */
    const val MAX = 3
    private const val PREF = "gleisbild.fenster"

    override val moduleId = TRACKPLAN_MODULE_ID

    private val opened = MutableStateFlow<List<String>>(emptyList())
    private var loaded = false

    override fun open(app: MobaApp): StateFlow<List<String>> {
        if (!loaded) {
            loaded = true
            opened.value = app.preference(PREF)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct()?.take(MAX).orEmpty()
        }
        return opened
    }

    private fun service(app: MobaApp) = app.session.value?.host?.service(TrackPlanService::class)

    override fun allowed(app: MobaApp): Boolean = ShellUiState.separateWindows.value && (service(app)?.maxVisiblePlans ?: 1) > 1

    override fun title(app: MobaApp, key: String): String =
        service(app)?.plans?.value?.firstOrNull { it.id == key }?.name ?: "Gleisbild"

    /**
     * Zeigt das Gleisbild [id] in einem eigenen Fenster und blendet es dafuer ein.
     * @return Hinweis, warum es nicht geht, sonst `null`
     */
    fun detach(app: MobaApp, id: String): String? {
        val service = service(app) ?: return "Nicht mit der Zentrale verbunden."
        if (!allowed(app)) return "Gleisbilder in eigenen Fenstern gibt es ab Pro."
        val list = open(app).value
        if (id in list) return null
        if (list.size >= MAX) return "Höchstens $MAX Gleisbilder in eigenen Fenstern."
        if (id !in service.visiblePlans.value) {
            try {
                service.toggleVisible(id)
            } catch (e: LicenseRequiredException) {
                return e.message
            }
        }
        store(app, list + id)
        return null
    }

    override fun close(app: MobaApp, key: String) {
        store(app, open(app).value - key)
    }

    private fun store(app: MobaApp, ids: List<String>) {
        opened.value = ids
        app.setPreference(PREF, ids.joinToString(","))
    }

    /** Nur fuer Tests und Vorschaubilder. */
    fun reset() {
        opened.value = emptyList()
        loaded = true
    }

    @Composable
    override fun Content(app: MobaApp, key: String) {
        val session by app.session.collectAsState()
        val service = session?.host?.service(TrackPlanService::class)
        if (service == null) {
            Text("Nicht mit der Zentrale verbunden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        val plans by service.plans.collectAsState()
        val plan = plans.firstOrNull { it.id == key }
        LaunchedEffect(service, key) {
            // Ein wieder geoeffnetes Fenster blendet sein Gleisbild ein; blendet man es im Hauptfenster aus, schliesst es
            if (key !in service.visiblePlans.value && runCatching { service.toggleVisible(key) }.isFailure) {
                close(app, key)
                return@LaunchedEffect
            }
            service.visiblePlans.collect { if (key !in it) close(app, key) }
        }
        if (plan == null) {
            // Geloeschtes Gleisbild: Fenster zu
            if (plans.isNotEmpty()) LaunchedEffect(key) { close(app, key) }
            return
        }
        val library = session?.host?.service(SymbolService::class)?.symbols?.collectAsState()?.value.orEmpty()
        val message by TrackPlanUiState.message.collectAsState()
        CompositionLocalProvider(LocalSymbolLibrary provides library) {
            val op = rememberPlanOperation(app, service)
            TrainDragLayer(service, Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp)) }
                BoxWithConstraints(Modifier.weight(1f)) {
                    // Gleisbild ins Fenster einpassen: so gross, wie der Bildschirm es hergibt
                    val inner = 26.dp
                    val fit = minOf((maxWidth - inner) / plan.width, (maxHeight - inner) / plan.height)
                    val cell = fit.coerceIn(MIN_CELL.dp, MAX_WINDOW_CELL.dp)
                    PlanCard(
                        plan, op.state, Modifier.fillMaxSize(), fill = true, onTap = op.onTap,
                        onContext = op.onContext, overlay = op.overlay, cellSize = cell, showTitle = false,
                    )
                }
            }
            }
        }
    }
}

/** Groesste Gleisgroesse im eigenen Fenster; reicht fuer kleine Gleisbilder auf grossen Bildschirmen. */
internal const val MAX_WINDOW_CELL = 160
