package de.moba.modules.actions

import de.moba.core.runtime.LicenseInfo
import de.moba.core.runtime.MobaModule
import de.moba.core.runtime.ModuleContext
import de.moba.core.runtime.SemVer
import de.moba.core.runtime.on
import de.moba.core.runtime.provide
import de.moba.core.runtime.service
import de.moba.core.station.CommandStation
import de.moba.core.station.Direction
import de.moba.core.station.LocoState
import de.moba.core.station.StationEvent
import de.moba.modules.actions.api.ACTIONS_MODULE_ID
import de.moba.modules.actions.api.ActionBook
import de.moba.modules.actions.api.ActionLicenseException
import de.moba.modules.actions.api.ActionLogEntry
import de.moba.modules.actions.api.ActionRule
import de.moba.modules.actions.api.ActionService
import de.moba.modules.actions.api.ActionStore
import de.moba.modules.actions.api.Command
import de.moba.modules.actions.api.CommandType
import de.moba.modules.actions.api.fuelMinutes
import de.moba.modules.driving.api.DrivingService
import de.moba.modules.routes.api.RouteService
import de.moba.modules.trackplan.api.ModelTime
import de.moba.modules.trackplan.api.TrackPlanService
import de.moba.modules.vehicles.api.Fleet
import de.moba.modules.vehicles.api.VehicleService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Haelt die Aktionen ueber Zentralenwechsel hinweg und speichert jede Aenderung. */
class ActionRepository(private val store: ActionStore?) {
    internal val book = MutableStateFlow(runCatching { store?.load() }.getOrNull() ?: ActionBook())

    internal fun change(save: Boolean = true, transform: (ActionBook) -> ActionBook) {
        val next = book.updateAndGet(transform)
        if (save) runCatching { store?.save(next) }
    }

    internal fun flush() {
        runCatching { store?.save(book.value) }
    }
}

/**
 * Modul "Aktionen": Bedingungen wie in iTrain pruefen und bei Erfuellung Befehle ausfuehren.
 * Bearbeiten ist kostenlos, Ausfuehren braucht Pro. Die Brennstoff-Simulation laeuft immer.
 */
/** Echte Uhrzeit in Minuten seit Mitternacht (Bedingung "Zeit" mit echter Uhr). */
expect fun localMinutes(): Int

class ActionModule(
    private val repository: ActionRepository,
    private val now: () -> Long = ModelTime::realMillis,
    private val wallMinutes: () -> Int = ::localMinutes,
    private val random: Random = Random.Default,
) : MobaModule {
    override val id = ACTIONS_MODULE_ID
    override val displayName = "Aktionen"
    override val version = SemVer(1, 0)
    override val apiVersion = SemVer(1, 0)
    override val licenseKey: String? = null

    private var service: ActionServiceImpl? = null

    override suspend fun start(ctx: ModuleContext) {
        val impl = ActionServiceImpl(
            repository, ctx.station, ctx.license, ctx.scope, now, wallMinutes, random,
            // Alle anderen Module sind freiwillig und werden erst bei Bedarf geholt
            Services(
                plan = { ctx.service<TrackPlanService>() },
                routes = { ctx.service<RouteService>() },
                driving = { ctx.service<DrivingService>() },
                vehicles = { ctx.service<VehicleService>() },
            ),
        )
        ctx.events.on<StationEvent>(ctx.scope) { impl.onEvent(it) }
        ctx.provide<ActionService>(impl)
        impl.start()
        service = impl
    }

    override suspend fun stop() {
        service?.stop()
    }

    companion object {
        val RUN_LICENSE_KEYS = listOf("pro", "club")

        /** So oft werden die Bedingungen geprueft und der Brennstoff verbraucht. */
        const val TICK_MILLIS = 200L
    }
}

internal class Services(
    val plan: () -> TrackPlanService? = { null },
    val routes: () -> RouteService? = { null },
    val driving: () -> DrivingService? = { null },
    val vehicles: () -> VehicleService? = { null },
)

internal class ActionServiceImpl(
    private val repository: ActionRepository,
    private val station: CommandStation,
    license: LicenseInfo,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val wallMinutes: () -> Int,
    private val random: Random,
    private val services: Services,
) : ActionService {
    override val book: StateFlow<ActionBook> = repository.book

    private val _satisfied = MutableStateFlow<Set<String>>(emptySet())
    override val satisfied: StateFlow<Set<String>> = _satisfied

    private val _running = MutableStateFlow<Set<String>>(emptySet())
    override val running: StateFlow<Set<String>> = _running

    private val _log = MutableStateFlow<List<ActionLogEntry>>(emptyList())
    override val log: StateFlow<List<ActionLogEntry>> = _log

    override val canRun = ActionModule.RUN_LICENSE_KEYS.any(license::isUnlocked)

    /** Was die Zentrale meldet; Fahren und Gleisbild ergaenzen das, wenn sie laufen. */
    private data class Reported(
        val connected: Boolean = true,
        val trackPower: Boolean = false,
        val feedback: Set<Block> = emptySet(),
        val turnouts: Map<Int, Boolean> = emptyMap(),
        val locos: Map<Int, LocoState> = emptyMap(),
    )

    // Ereignisse, Pruefschleife und Oberflaeche laufen auf verschiedenen Threads: nur atomar ersetzen
    private val reported = MutableStateFlow(Reported())
    private val jobs = MutableStateFlow<Map<String, Job>>(emptyMap())
    private val stoppedSince = mutableMapOf<Int, Long>()
    private var loop: Job? = null
    private var lastTick = 0L
    private var fuelSaved = 0L

    fun onEvent(event: StationEvent) {
        reported.update { r ->
            when (event) {
                is StationEvent.ConnectionChanged -> r.copy(connected = event.connected)
                is StationEvent.TrackPowerChanged -> r.copy(trackPower = event.on)
                is StationEvent.FeedbackChanged -> {
                    val f = event.module to event.contact
                    r.copy(feedback = if (event.occupied) r.feedback + f else r.feedback - f)
                }
                is StationEvent.TurnoutChanged -> r.copy(turnouts = r.turnouts + (event.address to event.thrown))
                is StationEvent.LocoChanged -> r.copy(locos = r.locos + (event.state.address to event.state))
                else -> r
            }
        }
    }

    fun start() {
        lastTick = now()
        fuelSaved = lastTick
        loop = scope.launch {
            while (isActive) {
                tick()
                delay(ActionModule.TICK_MILLIS)
            }
        }
    }

    fun stop() {
        loop?.cancel()
        jobs.value.values.forEach { it.cancel() }
        repository.flush()
    }

    internal fun world(): World {
        val t = now()
        val r = reported.value
        val plan = services.plan()
        val routes = services.routes()
        val driving = services.driving()
        val book = routes?.book?.value
        val active = routes?.activeRoutes?.value.orEmpty()
        val runningAutomations = routes?.automation?.value.orEmpty().filterValues { it.running }.keys
        val allLocos = r.locos + driving?.locos?.value.orEmpty()
        val trains = plan?.trains?.value.orEmpty()
        val feedbackNow = r.feedback + plan?.occupied?.value.orEmpty()
        val turnoutsNow = r.turnouts + plan?.turnouts?.value.orEmpty()
        // Wartezeit im Block: ab dem ersten Augenblick, in dem die Lok dort mit Fahrstufe 0 steht
        val standing = trains.values.filter { (allLocos[it]?.speed ?: 0) == 0 }.toSet()
        stoppedSince.keys.retainAll(standing)
        standing.forEach { stoppedSince.getOrPut(it) { t } }
        return World(
            now = t,
            modelMinutes = plan?.clock?.value?.minutesAt(t),
            wallMinutes = wallMinutes(),
            connected = r.connected,
            trackPower = r.trackPower,
            feedback = feedbackNow,
            turnouts = turnoutsNow,
            reservedAddresses = book?.routes.orEmpty().filter { it.id in active }.flatMap { r -> r.settings.map { it.address } }.toSet(),
            reservedBlocks = plan?.reservations?.value.orEmpty().keys,
            trains = trains,
            locos = allLocos,
            stoppedSince = stoppedSince.toMap(),
            automatedLocos = book?.let { b ->
                b.shuttles.filter { it.id in runningAutomations }.map { it.locoAddress } +
                    b.trainRoutes.filter { it.id in runningAutomations }.map { it.locoAddress }
            }.orEmpty().toSet(),
            runningAutomations = runningAutomations,
            fleet = services.vehicles()?.fleet?.value ?: Fleet(),
            fuel = repository.book.value.fuel,
            boosters = plan?.objects?.value?.boosters.orEmpty().flatMap { b ->
                val on = b.isOn(feedbackNow, turnoutsNow)
                listOf(b.id to on, b.name.trim().lowercase() to on)
            }.toMap(),
        )
    }

    internal fun tick() {
        val w = world()
        burnFuel(w)
        val before = _satisfied.value
        val now = mutableSetOf<String>()
        for (rule in repository.book.value.rules) {
            if (!rule.enabled) continue
            if (!Conditions.eval(rule.condition, w)) continue
            now += rule.id
            // Nur beim Wechsel zu "erfuellt" ausfuehren; erst dann wird der Zufall ausgewuerfelt
            if (rule.id !in before && canRun && rule.id !in jobs.value) {
                if (Conditions.eval(rule.condition, w) { percent -> random.nextInt(100) < percent }) execute(rule, "Bedingung erfüllt")
            }
        }
        _satisfied.value = now
    }

    /** Verbraucht Brennstoff nach Fahrstufe und Antrieb (Dampf 20 min, Diesel 60 min bei voller Fahrt). */
    private fun burnFuel(w: World) {
        val elapsed = (w.now - lastTick).coerceAtLeast(0)
        lastTick = w.now
        if (elapsed == 0L) return
        val use = w.locos.values.mapNotNull { s ->
            if (s.speed <= 0) return@mapNotNull null
            val minutes = w.record(s.address)?.traction.fuelMinutes() ?: return@mapNotNull null
            s.address to 100.0 * elapsed / (minutes * 60_000) * s.speed / 126.0
        }
        if (use.isEmpty()) return
        // Nicht bei jedem Schritt auf die Platte schreiben, hoechstens alle 30 Sekunden
        val save = w.now - fuelSaved >= FUEL_SAVE_MILLIS
        if (save) fuelSaved = w.now
        repository.change(save) { b ->
            b.copy(fuel = b.fuel + use.map { (a, u) -> a to ((b.fuel[a] ?: 100.0) - u).coerceAtLeast(0.0) })
        }
    }

    private fun log(text: String, error: Boolean = false) {
        _log.update { (listOf(ActionLogEntry(now(), text, error)) + it).take(ActionService.MAX_LOG) }
    }

    private fun execute(rule: ActionRule, reason: String) {
        _running.update { it + rule.id }
        log("„${rule.name}“: $reason")
        // Erst eintragen, dann starten: sonst koennte die fertige Aktion vor dem Eintrag austragen
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                for (c in rule.commands) {
                    try {
                        perform(c)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log("„${rule.name}“: ${e.message ?: e::class.simpleName}", error = true)
                    }
                }
            } finally {
                jobs.update { it - rule.id }
                _running.update { it - rule.id }
            }
        }
        jobs.update { it + (rule.id to job) }
        job.start()
    }

    private suspend fun perform(c: Command) {
        fun int(key: String) = c.param(key).trim().toIntOrNull() ?: throw IllegalArgumentException("${c.type.label}: „${c.type.params.first { it.key == key }.label}“ fehlt")
        fun need(key: String) = c.param(key).ifBlank { throw IllegalArgumentException("${c.type.label}: nichts gewählt") }
        val plan = services.plan()
        val routes = services.routes()
        val driving = services.driving()
        when (c.type) {
            CommandType.ACCESSORY -> {
                val a = int("adresse")
                val thrown = c.param("stellung") == "abzweigend"
                if (plan != null) plan.setTurnout(a, thrown) else station.setTurnout(a, thrown)
                reported.update { it.copy(turnouts = it.turnouts + (a to thrown)) }
            }
            CommandType.ROUTE_SET -> (routes ?: noRoutes()).setRoute(need("fahrstrasse"))
            CommandType.ROUTE_RELEASE -> (routes ?: noRoutes()).releaseRoute(need("fahrstrasse"))
            CommandType.AUTOMATION_START -> {
                val r = routes ?: noRoutes()
                val id = need("zugroute")
                if (r.book.value.shuttles.any { it.id == id }) r.startShuttle(id) else r.startTrainRoute(id)
            }
            CommandType.AUTOMATION_STOP -> (routes ?: noRoutes()).stopAutomation(need("zugroute"))
            CommandType.LOCO_SPEED -> {
                val a = int("lok")
                val dir = if (c.param("richtung") == "rueck") Direction.REVERSE else Direction.FORWARD
                val speed = int("fahrstufe").coerceIn(0, 126)
                if (driving != null) driving.drive(a, speed, dir) else station.setLocoSpeed(a, speed, dir)
            }
            CommandType.LOCO_STOP -> {
                val a = int("lok")
                if (driving != null) driving.stop(a) else station.setLocoSpeed(a, 0, reported.value.locos[a]?.direction ?: Direction.FORWARD)
            }
            CommandType.LOCO_FUNCTION -> {
                val a = int("lok")
                val on = c.param("zustand") != "aus"
                if (driving != null) driving.setFunction(a, int("funktion"), on) else station.setLocoFunction(a, int("funktion"), on)
            }
            CommandType.TRACK_POWER -> station.setTrackPower(c.param("zustand") != "aus")
            CommandType.EMERGENCY_STOP -> station.emergencyStop()
            CommandType.WAIT -> delay(int("sekunden").coerceIn(0, 3600) * 1000L)
            CommandType.CLOCK -> {
                val p = plan ?: throw IllegalStateException("Die Modellzeit-Uhr gehört zum Gleisbild-Modul.")
                if (c.param("zustand") == "stop") p.stopClock() else p.startClock()
            }
            CommandType.REFUEL -> refuel(int("lok"))
            CommandType.BLOCK_RELEASE -> {
                val b = Conditions.parseBlock(c.param("block")) ?: throw IllegalArgumentException("Block freigeben: Block fehlt")
                (plan ?: throw IllegalStateException("Blöcke gehören zum Gleisbild-Modul.")).release(b)
            }
            CommandType.MESSAGE -> log(c.param("text"))
        }
    }

    private fun noRoutes(): Nothing = throw IllegalStateException("Das Modul Fahrstraßen läuft nicht.")

    override fun save(rule: ActionRule) = repository.change { b ->
        val i = b.rules.indexOfFirst { it.id == rule.id }
        b.copy(rules = if (i >= 0) b.rules.toMutableList().also { it[i] = rule } else b.rules + rule)
    }

    override fun delete(id: String) {
        jobs.value[id]?.cancel()
        repository.change { b -> b.copy(rules = b.rules.filter { it.id != id }) }
    }

    override fun setEnabled(id: String, enabled: Boolean) = repository.change { b ->
        b.copy(rules = b.rules.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    override fun newId(): String {
        val used = repository.book.value.rules.map { it.id }.toSet()
        return generateSequence(1) { it + 1 }.map { "ak-$it" }.first { it !in used }
    }

    override fun run(id: String) {
        if (!canRun) throw ActionLicenseException("Aktionen ausführen braucht das Pro-Abo.")
        val rule = repository.book.value.rules.firstOrNull { it.id == id } ?: return
        if (id in jobs.value) return
        execute(rule, "von Hand gestartet")
    }

    override fun refuel(address: Int) {
        repository.change { b -> b.copy(fuel = b.fuel + (address to 100.0)) }
        log("Lok $address aufgetankt")
    }

    companion object {
        const val FUEL_SAVE_MILLIS = 30_000L
    }
}
