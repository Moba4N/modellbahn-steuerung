package de.moba.modules.routes.api

import kotlinx.coroutines.flow.StateFlow

const val ROUTES_MODULE_ID = "moba.fahrstrassen"

/** Ein Rueckmelder: s88-Modul und Kontakt (1 bis 16). */
data class Feedback(val module: Int, val contact: Int) {
    override fun toString() = "$module.$contact"
}

/** Eine Stellung in einer Fahrstrasse: Weichen- oder Signaladresse gerade/Fahrt (false) oder abzweigend/Halt (true). */
data class RouteSetting(val address: Int, val thrown: Boolean)

/**
 * Fahrstrasse: alle Weichen und Signale zwischen zwei Punkten. Beim Stellen werden sie der Reihe nach
 * geschaltet; solange die Fahrstrasse gestellt ist, kann keine andere dieselben Adressen benutzen.
 * Wird [end] belegt, loest sie sich von selbst auf (der Zug ist angekommen).
 */
data class Route(
    val id: String,
    val name: String,
    val settings: List<RouteSetting> = emptyList(),
    val start: Feedback? = null,
    val end: Feedback? = null,
    /** Gleisbild, in dem die Fahrstrasse ausgewaehlt wurde, und ihr Weg als Rasterzellen (x, y); leer = von Hand angelegt. */
    val planId: String = "",
    val path: List<Pair<Int, Int>> = emptyList(),
)

/**
 * Pendelstrecke: eine Lok faehrt zwischen [endA] und [endB] hin und her und wartet an jedem Ende [waitSeconds].
 * Optionale Bremsmelder schalten vorher auf [brakeSpeed] herunter.
 */
data class Shuttle(
    val id: String,
    val name: String,
    val locoAddress: Int,
    val speed: Int = 60,
    val endA: Feedback? = null,
    val endB: Feedback? = null,
    val brakeA: Feedback? = null,
    val brakeB: Feedback? = null,
    val brakeSpeed: Int = 20,
    val waitSeconds: Int = 10,
)

/** Ein Abschnitt einer Zugroute: Fahrstrasse stellen, bis [stopAt] fahren, dort [waitSeconds] halten. */
data class TrainRouteStep(
    val routeId: String,
    val stopAt: Feedback? = null,
    val waitSeconds: Int = 0,
    /** Eigene Fahrstufe fuer diesen Abschnitt; `null` = die der Zugroute. */
    val speed: Int? = null,
)

/** Zugroute: eine Lok faehrt nacheinander ueber mehrere Fahrstrassen; mit [repeat] immer wieder von vorn. */
data class TrainRoute(
    val id: String,
    val name: String,
    val locoAddress: Int,
    val speed: Int = 60,
    val forward: Boolean = true,
    val steps: List<TrainRouteStep> = emptyList(),
    val repeat: Boolean = false,
)

/** Alles, was das Modul speichert. */
data class RouteBook(
    val routes: List<Route> = emptyList(),
    val shuttles: List<Shuttle> = emptyList(),
    val trainRoutes: List<TrainRoute> = emptyList(),
)

/** Liest und schreibt alle Fahrstrassen, Pendelstrecken und Zugrouten. */
interface RouteStore {
    fun load(): RouteBook?
    fun save(book: RouteBook)
}

/** Was eine laufende Automatik gerade tut. */
data class AutomationStatus(val text: String, val running: Boolean = true)

/**
 * Oeffentliche Schnittstelle des Moduls "Fahrstrassen". Anlegen und Bearbeiten ist immer moeglich;
 * Fahrstrassen stellen braucht Basis, Pendelzuege und Zugrouten fahren lassen braucht Pro.
 */
interface RouteService {
    val book: StateFlow<RouteBook>

    /** Ids der gerade gestellten Fahrstrassen. */
    val activeRoutes: StateFlow<Set<String>>

    /** Status laufender und zuletzt beendeter Pendelzuege und Zugrouten, nach Id. */
    val automation: StateFlow<Map<String, AutomationStatus>>

    val canSetRoutes: Boolean
    val canAutomate: Boolean

    fun saveRoute(route: Route)
    fun deleteRoute(id: String)
    fun saveShuttle(shuttle: Shuttle)
    fun deleteShuttle(id: String)
    fun saveTrainRoute(trainRoute: TrainRoute)
    fun deleteTrainRoute(id: String)

    /** Neue freie Id mit [prefix], z. B. "fs-". */
    fun newId(prefix: String): String

    /**
     * Stellt eine Fahrstrasse. @throws RouteConflictException wenn eine gestellte Fahrstrasse dieselben Adressen nutzt
     * @throws de.moba.modules.routes.api.RouteLicenseException ohne Basis
     */
    suspend fun setRoute(id: String)
    fun releaseRoute(id: String)

    /** Startet einen Pendelzug oder eine Zugroute. @throws RouteLicenseException ohne Pro */
    fun startShuttle(id: String)
    fun startTrainRoute(id: String)

    /** Haelt Pendelzug oder Zugroute an (Lok stoppt). */
    fun stopAutomation(id: String)
}

class RouteConflictException(message: String) : Exception(message)
class RouteLicenseException(message: String) : Exception(message)

/** Textformat: eine Zeile je Eintrag, Felder mit Tab getrennt. */
object RouteCodec {
    private const val HEADER = "# fahrstrassen v1"

    fun encode(book: RouteBook): String = buildString {
        appendLine(HEADER)
        for (r in book.routes) {
            line(
                "FS", r.id, r.name, r.settings.joinToString(" ") { "${it.address}:${if (it.thrown) "a" else "g"}" }, fb(r.start), fb(r.end),
                r.planId, r.path.joinToString(" ") { "${it.first},${it.second}" },
            )
        }
        for (s in book.shuttles) {
            line("PE", s.id, s.name, s.locoAddress, s.speed, fb(s.endA), fb(s.endB), fb(s.brakeA), fb(s.brakeB), s.brakeSpeed, s.waitSeconds)
        }
        for (t in book.trainRoutes) {
            val steps = t.steps.joinToString(" ") { "${it.routeId},${fb(it.stopAt)},${it.waitSeconds},${it.speed ?: ""}" }
            line("ZR", t.id, t.name, t.locoAddress, t.speed, if (t.forward) "v" else "r", if (t.repeat) "1" else "0", steps)
        }
    }

    fun decode(text: String): RouteBook {
        val routes = mutableListOf<Route>()
        val shuttles = mutableListOf<Shuttle>()
        val trains = mutableListOf<TrainRoute>()
        for (raw in text.lineSequence()) {
            if (raw.isBlank() || raw.startsWith("#")) continue
            val f = raw.split('\t')
            fun at(i: Int) = f.getOrElse(i) { "" }
            fun int(i: Int, default: Int) = at(i).toIntOrNull() ?: default
            runCatching {
                when (f[0]) {
                    "FS" -> routes += Route(
                        at(1), at(2),
                        at(3).split(' ').filter { it.isNotBlank() }.mapNotNull { token ->
                            val (a, p) = token.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                            RouteSetting(a.toIntOrNull() ?: return@mapNotNull null, p == "a")
                        },
                        parseFb(at(4)), parseFb(at(5)),
                        at(6),
                        at(7).split(' ').mapNotNull { token ->
                            val xy = token.split(',')
                            if (xy.size != 2) return@mapNotNull null
                            (xy[0].toIntOrNull() ?: return@mapNotNull null) to (xy[1].toIntOrNull() ?: return@mapNotNull null)
                        },
                    )
                    "PE" -> shuttles += Shuttle(
                        at(1), at(2), int(3, 3), int(4, 60), parseFb(at(5)), parseFb(at(6)), parseFb(at(7)), parseFb(at(8)),
                        int(9, 20), int(10, 10),
                    )
                    "ZR" -> trains += TrainRoute(
                        at(1), at(2), int(3, 3), int(4, 60), at(5) != "r",
                        at(7).split(' ').filter { it.isNotBlank() }.map { token ->
                            val p = token.split(',')
                            TrainRouteStep(p[0], parseFb(p.getOrElse(1) { "" }), p.getOrElse(2) { "0" }.toIntOrNull() ?: 0, p.getOrElse(3) { "" }.toIntOrNull())
                        },
                        at(6) == "1",
                    )
                }
            }
        }
        return RouteBook(routes, shuttles, trains)
    }

    private fun StringBuilder.line(vararg fields: Any?) =
        appendLine(fields.joinToString("\t") { it.toString().replace('\t', ' ').replace('\n', ' ') })

    private fun fb(f: Feedback?) = f?.let { "${it.module}.${it.contact}" } ?: ""

    private fun parseFb(s: String): Feedback? {
        val p = s.split('.')
        if (p.size != 2) return null
        return Feedback(p[0].toIntOrNull() ?: return null, p[1].toIntOrNull() ?: return null)
    }
}
