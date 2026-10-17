package de.moba.modules.routes

import de.moba.core.runtime.LicenseInfo
import de.moba.core.runtime.MobaModule
import de.moba.core.runtime.ModuleContext
import de.moba.core.runtime.SemVer
import de.moba.core.runtime.on
import de.moba.core.runtime.provide
import de.moba.core.runtime.service
import de.moba.core.station.CommandStation
import de.moba.core.station.Direction
import de.moba.core.station.StationEvent
import de.moba.modules.routes.api.AutomationStatus
import de.moba.modules.routes.api.Feedback
import de.moba.modules.routes.api.ROUTES_MODULE_ID
import de.moba.modules.routes.api.Route
import de.moba.modules.routes.api.RouteBook
import de.moba.modules.routes.api.RouteConflictException
import de.moba.modules.routes.api.RouteLicenseException
import de.moba.modules.routes.api.RouteService
import de.moba.modules.routes.api.RouteStore
import de.moba.modules.routes.api.Shuttle
import de.moba.modules.routes.api.TrainRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import de.moba.modules.trackplan.api.BlockException
import de.moba.modules.trackplan.api.BlockReservation
import de.moba.modules.trackplan.api.FeedbackRef
import de.moba.modules.trackplan.api.TrackPlanService
import de.moba.modules.trackplan.api.block
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Haelt Fahrstrassen, Pendelstrecken und Zugrouten ueber Zentralenwechsel hinweg und speichert jede Aenderung. */
class RouteRepository(private val store: RouteStore?) {
    internal val book = MutableStateFlow(runCatching { store?.load() }.getOrNull() ?: RouteBook())

    internal fun change(transform: (RouteBook) -> RouteBook) {
        val next = transform(book.value)
        runCatching { store?.save(next) }
        book.value = next
    }
}

/**
 * Modul "Fahrstrassen": Fahrstrassen, Pendelstrecken und Zugrouten anlegen und fahren.
 * Anlegen ist kostenlos, Fahrstrassen stellen ab Basis, Automatik (Pendel, Zugrouten) ab Pro.
 */
class RouteModule(private val repository: RouteRepository) : MobaModule {
    override val id = ROUTES_MODULE_ID
    override val displayName = "Fahrstraßen"
    override val version = SemVer(1, 0)
    override val apiVersion = SemVer(1, 0)
    override val licenseKey: String? = null

    private var service: RouteServiceImpl? = null

    override suspend fun start(ctx: ModuleContext) {
        val impl = RouteServiceImpl(
            repository, ctx.station, ctx.license, ctx.scope,
            stopTiming = { address, speed -> stopTiming(ctx, address, speed) },
            // Das Gleisbild ist freiwillig; ohne es gibt es keine Blockreservierung
            blocks = { ctx.service<TrackPlanService>() },
        )
        ctx.events.on<StationEvent.FeedbackChanged>(ctx.scope) { if (it.occupied) impl.onOccupied(Feedback(it.module, it.contact)) }
        ctx.provide<RouteService>(impl)
        service = impl
    }

    override suspend fun stop() {
        service?.stopAll()
    }

    companion object {
        val SET_LICENSE_KEYS = listOf("basis", "pro", "club")
        val AUTOMATION_LICENSE_KEYS = listOf("pro", "club")

        /** Pause zwischen zwei Weichenbefehlen, damit Decoder und Zentrale nicht ueberlastet werden. */
        const val SWITCH_PAUSE_MILLIS = 150L

        /** So oft fragt eine Zugroute nach, ob ihre Fahrstrasse frei geworden ist. */
        const val RETRY_MILLIS = 1000L
    }
}

internal class RouteServiceImpl(
    private val repository: RouteRepository,
    private val station: CommandStation,
    license: LicenseInfo,
    private val scope: CoroutineScope,
    /** Rueckmelde-Offset und Reaktionsverzoegerung der Lok bei dieser Fahrstufe; `null` = sofort halten. */
    private val stopTiming: (address: Int, speed: Int) -> StopTiming? = { _, _ -> null },
    /** Bloecke und Zuege im Gleisbild: Fahrstrassen reservieren ihre Bloecke, Zuege wandern mit. */
    private val blocks: () -> TrackPlanService? = { null },
) : RouteService {
    override val book: StateFlow<RouteBook> = repository.book

    private val _active = MutableStateFlow<Set<String>>(emptySet())
    override val activeRoutes: StateFlow<Set<String>> = _active

    private val _automation = MutableStateFlow<Map<String, AutomationStatus>>(emptyMap())
    override val automation: StateFlow<Map<String, AutomationStatus>> = _automation

    override val canSetRoutes = RouteModule.SET_LICENSE_KEYS.any(license::isUnlocked)
    override val canAutomate = RouteModule.AUTOMATION_LICENSE_KEYS.any(license::isUnlocked)

    private val occupied = MutableSharedFlow<Feedback>(extraBufferCapacity = 64)
    private val lock = Mutex()
    private val watchers = mutableMapOf<String, Job>()
    private val runs = mutableMapOf<String, Job>()

    fun onOccupied(feedback: Feedback) {
        occupied.tryEmit(feedback)
    }

    private suspend fun waitFor(feedback: Feedback) {
        occupied.first { it == feedback }
    }

    /** Am Haltemelder halten: erst noch den Rueckmelde-Offset weiterfahren, abzueglich der Reaktionsverzoegerung. */
    private suspend fun stopAt(feedback: Feedback, address: Int, speed: Int, direction: Direction) {
        waitFor(feedback)
        follow(address, feedback)
        val wait = stopTiming(address, speed)?.delayMillis() ?: 0L
        if (wait > 0) delay(wait)
        station.setLocoSpeed(address, 0, direction)
    }

    /** Steht die Lok in einem Block des Gleisbilds, wandert sie in den Block von [feedback] mit. */
    private fun follow(address: Int, feedback: Feedback) {
        val plans = blocks() ?: return
        val from = plans.trains.value.entries.firstOrNull { it.value == address }?.key ?: return
        runCatching { plans.moveTrain(from, feedback.module to feedback.contact) }
    }

    /** Bloecke einer Fahrstrasse: Start, Ziel und alle Rueckmelder auf ihrem Weg im Gleisbild. */
    private fun routeBlocks(route: Route, plans: TrackPlanService): Set<FeedbackRef> {
        val onPath = plans.plans.value.firstOrNull { it.id == route.planId }?.let { plan ->
            route.path.mapNotNull { (x, y) -> plan.tileAt(x, y)?.block() }
        }.orEmpty()
        return (listOfNotNull(route.start, route.end).map { it.module to it.contact } + onPath).toSet()
    }

    private fun owner(id: String) = "fs:$id"

    // ------------------------------------------------------------ Bearbeiten

    override fun saveRoute(route: Route) {
        require(route.name.isNotBlank()) { "Bitte einen Namen eingeben." }
        require(route.settings.all { it.address in 1..2048 }) { "Weichenadressen liegen zwischen 1 und 2048." }
        require(route.settings.map { it.address }.toSet().size == route.settings.size) { "Jede Adresse darf nur einmal vorkommen." }
        repository.change { b -> b.copy(routes = b.routes.upsert(route) { it.id }) }
    }

    override fun deleteRoute(id: String) {
        releaseRoute(id)
        repository.change { b -> b.copy(routes = b.routes.filterNot { it.id == id }) }
    }

    override fun saveShuttle(shuttle: Shuttle) {
        require(shuttle.name.isNotBlank()) { "Bitte einen Namen eingeben." }
        require(shuttle.locoAddress in 1..10239) { "Die Lokadresse muss zwischen 1 und 10239 liegen." }
        repository.change { b -> b.copy(shuttles = b.shuttles.upsert(shuttle) { it.id }) }
    }

    override fun deleteShuttle(id: String) {
        stopAutomation(id)
        repository.change { b -> b.copy(shuttles = b.shuttles.filterNot { it.id == id }) }
    }

    override fun saveTrainRoute(trainRoute: TrainRoute) {
        require(trainRoute.name.isNotBlank()) { "Bitte einen Namen eingeben." }
        require(trainRoute.locoAddress in 1..10239) { "Die Lokadresse muss zwischen 1 und 10239 liegen." }
        repository.change { b -> b.copy(trainRoutes = b.trainRoutes.upsert(trainRoute) { it.id }) }
    }

    override fun deleteTrainRoute(id: String) {
        stopAutomation(id)
        repository.change { b -> b.copy(trainRoutes = b.trainRoutes.filterNot { it.id == id }) }
    }

    override fun newId(prefix: String): String {
        val b = book.value
        val used = (b.routes.map { it.id } + b.shuttles.map { it.id } + b.trainRoutes.map { it.id }).toSet()
        return generateSequence(1) { it + 1 }.map { "$prefix$it" }.first { it !in used }
    }

    private fun <T> List<T>.upsert(item: T, key: (T) -> String): List<T> =
        if (any { key(it) == key(item) }) map { if (key(it) == key(item)) item else it } else this + item

    // ------------------------------------------------------------ Fahrstrassen

    override suspend fun setRoute(id: String) {
        if (!canSetRoutes) throw RouteLicenseException("Fahrstraßen stellen gibt es ab der Basis-Lizenz.")
        val route = book.value.routes.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Fahrstraße nicht gefunden")
        lock.withLock {
            if (id in _active.value) return
            val addresses = route.settings.map { it.address }.toSet()
            val blocking = _active.value.mapNotNull { other -> book.value.routes.firstOrNull { it.id == other } }
                .firstOrNull { other -> other.settings.any { it.address in addresses } }
            if (blocking != null) throw RouteConflictException("„${route.name}“ kreuzt die gestellte Fahrstraße „${blocking.name}“.")
            blocks()?.let { plans ->
                try {
                    plans.reserve(routeBlocks(route, plans), BlockReservation(owner(id), "Fahrstraße „${route.name}“"))
                } catch (e: BlockException) {
                    throw RouteConflictException(e.message ?: "Block reserviert")
                }
            }
            _active.update { it + id }
        }
        try {
            route.settings.forEachIndexed { i, s ->
                if (i > 0) delay(RouteModule.SWITCH_PAUSE_MILLIS)
                station.setTurnout(s.address, s.thrown)
            }
        } catch (e: Exception) {
            releaseRoute(id)
            throw e
        }
        // Am Ziel angekommen: Fahrstrasse loest sich selbst auf
        route.end?.let { end ->
            watchers[id]?.cancel()
            watchers[id] = scope.launch {
                waitFor(end)
                // Der Zug aus dem Startblock ist jetzt im Zielblock
                val plans = blocks()
                val start = route.start
                if (plans != null && start != null) {
                    runCatching { plans.moveTrain(start.module to start.contact, end.module to end.contact) }
                }
                releaseRoute(id)
            }
        }
    }

    override fun releaseRoute(id: String) {
        watchers.remove(id)?.cancel()
        _active.update { it - id }
        blocks()?.releaseOwner(owner(id))
    }

    // ------------------------------------------------------------ Automatik

    override fun startShuttle(id: String) {
        checkAutomation()
        val shuttle = book.value.shuttles.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Pendelstrecke nicht gefunden")
        val a = shuttle.endA
        val b = shuttle.endB
        require(a != null && b != null) { "Bitte beide Endmelder eintragen." }
        require(a != b) { "Die beiden Endmelder müssen verschieden sein." }
        launchRun(id, shuttle.locoAddress, Direction.FORWARD) {
            var direction = Direction.FORWARD
            while (true) {
                val toB = direction == Direction.FORWARD
                val target = if (toB) b else a
                val brake = if (toB) shuttle.brakeB else shuttle.brakeA
                status(id, "Fährt nach ${if (toB) "B" else "A"} (Melder $target)")
                station.setLocoSpeed(shuttle.locoAddress, shuttle.speed, direction)
                if (brake != null) {
                    waitFor(brake)
                    station.setLocoSpeed(shuttle.locoAddress, shuttle.brakeSpeed, direction)
                }
                stopAt(target, shuttle.locoAddress, if (brake != null) shuttle.brakeSpeed else shuttle.speed, direction)
                status(id, "Wartet ${shuttle.waitSeconds} s an ${if (toB) "B" else "A"}")
                delay(shuttle.waitSeconds * 1000L)
                direction = if (toB) Direction.REVERSE else Direction.FORWARD
            }
        }
    }

    override fun startTrainRoute(id: String) {
        checkAutomation()
        val train = book.value.trainRoutes.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Zugroute nicht gefunden")
        require(train.steps.isNotEmpty()) { "Die Zugroute hat noch keine Abschnitte." }
        val routes = train.steps.map { step ->
            book.value.routes.firstOrNull { it.id == step.routeId } ?: throw IllegalArgumentException("Eine Fahrstraße der Zugroute fehlt.")
        }
        train.steps.forEachIndexed { i, step ->
            require((step.stopAt ?: routes[i].end) != null) { "Abschnitt ${i + 1}: Bitte einen Haltemelder eintragen (oder ein Ziel in der Fahrstraße)." }
        }
        val direction = if (train.forward) Direction.FORWARD else Direction.REVERSE
        launchRun(id, train.locoAddress, direction) { held ->
            do {
                for ((i, step) in train.steps.withIndex()) {
                    val route = routes[i]
                    // Fahrstrasse stellen, sobald sie frei ist
                    while (true) {
                        try {
                            setRoute(route.id)
                            held += route.id
                            break
                        } catch (e: RouteConflictException) {
                            status(id, "Abschnitt ${i + 1}: wartet auf freie Fahrstraße „${route.name}“")
                            delay(RouteModule.RETRY_MILLIS)
                        }
                    }
                    val stop = step.stopAt ?: route.end!!
                    status(id, "Abschnitt ${i + 1} von ${train.steps.size}: „${route.name}“ bis Melder $stop")
                    val speed = step.speed ?: train.speed
                    station.setLocoSpeed(train.locoAddress, speed, direction)
                    stopAt(stop, train.locoAddress, speed, direction)
                    releaseRoute(route.id)
                    held -= route.id
                    if (step.waitSeconds > 0) {
                        status(id, "Abschnitt ${i + 1}: hält ${step.waitSeconds} s")
                        delay(step.waitSeconds * 1000L)
                    }
                }
            } while (train.repeat)
            status(id, "Am Ziel angekommen", running = false)
        }
    }

    override fun stopAutomation(id: String) {
        runs.remove(id)?.cancel()
    }

    fun stopAll() {
        runs.values.toList().forEach { it.cancel() }
        runs.clear()
    }

    private fun checkAutomation() {
        if (!canAutomate) throw RouteLicenseException("Pendelzüge und Zugrouten gibt es ab Pro.")
    }

    private fun status(id: String, text: String, running: Boolean = true) =
        _automation.update { it + (id to AutomationStatus(text, running)) }

    /** Startet eine Automatik; beim Abbrechen oder Fehler haelt die Lok an und gehaltene Fahrstrassen werden frei. */
    private fun launchRun(id: String, loco: Int, direction: Direction, block: suspend (MutableSet<String>) -> Unit) {
        val b = book.value
        val busy = (b.shuttles.filter { it.locoAddress == loco }.map { it.id } + b.trainRoutes.filter { it.locoAddress == loco }.map { it.id })
            .any { it != id && runs[it]?.isActive == true }
        require(!busy) { "Lok $loco fährt schon in einer anderen Automatik." }
        runs[id]?.cancel()
        val held = mutableSetOf<String>()
        runs[id] = scope.launch {
            try {
                block(held)
            } catch (e: CancellationException) {
                status(id, "Angehalten", running = false)
                throw e
            } catch (e: Exception) {
                status(id, "Abgebrochen: ${e.message}", running = false)
            } finally {
                withContext(NonCancellable) {
                    runCatching { station.setLocoSpeed(loco, 0, direction) }
                    held.forEach(::releaseRoute)
                }
            }
        }
    }
}
