package de.moba.modules.trackplan

import de.moba.core.runtime.LicenseInfo
import de.moba.core.runtime.MobaModule
import de.moba.core.runtime.ModuleContext
import de.moba.core.runtime.SemVer
import de.moba.core.runtime.on
import de.moba.core.runtime.provide
import de.moba.core.station.CommandStation
import de.moba.core.station.StationEvent
import de.moba.modules.trackplan.api.Branch
import de.moba.modules.trackplan.api.LicenseRequiredException
import de.moba.modules.trackplan.api.SignalAddressing
import de.moba.modules.trackplan.api.TRACKPLAN_MODULE_ID
import de.moba.modules.trackplan.api.TileKind
import de.moba.modules.trackplan.api.TrackPlan
import de.moba.modules.trackplan.api.TrackPlanService
import de.moba.modules.trackplan.api.TrackPlanStore
import de.moba.modules.trackplan.api.TrackShape
import de.moba.modules.trackplan.api.TrackTile
import de.moba.modules.trackplan.api.activeBranch
import de.moba.modules.trackplan.api.AccessoryProps
import de.moba.modules.trackplan.api.FeedbackList
import de.moba.modules.trackplan.api.TableCommand
import de.moba.modules.trackplan.api.TableConfig
import de.moba.modules.trackplan.api.aspectNames
import de.moba.modules.trackplan.api.isOn
import de.moba.modules.trackplan.api.outputFor
import de.moba.modules.trackplan.api.pulseMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Haelt die Gleisbilder ueber Zentralenwechsel hinweg. Die App erzeugt ihn einmal und reicht ihn
 * jedem neu gestarteten [TrackPlanModule] mit. Ohne gespeicherte Plaene startet er mit der Beispielanlage.
 */
class TrackPlanRepository(private val store: TrackPlanStore?) {
    internal val plans = MutableStateFlow(
        runCatching { store?.load() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: listOf(DemoPlans.layout()),
    )
    internal val unsaved = MutableStateFlow(false)
    internal val visible = MutableStateFlow(listOf(plans.value.first().id))
    private var nextNumber = plans.value.size + 1

    internal fun newId(): String {
        while (true) {
            val id = "plan-${nextNumber++}"
            if (plans.value.none { it.id == id }) return id
        }
    }

    internal fun saveAll() {
        val s = store ?: return
        s.save(plans.value)
        unsaved.value = false
    }
}

/**
 * Modul "Gleisbild" (M3): Gleisplaene zeichnen, bis zu drei gleichzeitig anzeigen und Weichen schalten.
 * Zeichnen ist kostenlos, Speichern braucht ein Abo, mehr als ein Gleisbild gleichzeitig braucht Pro.
 */
class TrackPlanModule(private val repository: TrackPlanRepository) : MobaModule {
    override val id = TRACKPLAN_MODULE_ID
    override val displayName = "Gleisbild"
    override val version = SemVer(1, 0)
    override val apiVersion = SemVer(1, 0)
    override val licenseKey: String? = null

    override suspend fun start(ctx: ModuleContext) {
        val service = TrackPlanServiceImpl(repository, ctx.station, ctx.license, ctx.scope)
        ctx.events.on<StationEvent.TurnoutChanged>(ctx.scope) { service.onTurnout(it.address, it.thrown) }
        ctx.events.on<StationEvent.FeedbackChanged>(ctx.scope) { service.onFeedback(it.module, it.contact, it.occupied) }
        ctx.provide<TrackPlanService>(service)
    }

    companion object {
        /** Jedes Abo erlaubt Speichern. */
        val SAVE_LICENSE_KEYS = listOf("basis", "pro", "club")

        /** Mehrere Gleisbilder gleichzeitig gibt es ab Pro. */
        val MULTI_VIEW_LICENSE_KEYS = listOf("pro", "club")
        const val FREE_VISIBLE_PLANS = 1
        const val PRO_VISIBLE_PLANS = 3
    }
}

internal class TrackPlanServiceImpl(
    private val repository: TrackPlanRepository,
    private val station: CommandStation,
    license: LicenseInfo,
    private val scope: CoroutineScope,
) : TrackPlanService {
    override val plans: StateFlow<List<TrackPlan>> = repository.plans
    override val unsaved: StateFlow<Boolean> = repository.unsaved
    override val visiblePlans: StateFlow<List<String>> = repository.visible

    override val canSave = TrackPlanModule.SAVE_LICENSE_KEYS.any(license::isUnlocked)
    override val maxVisiblePlans =
        if (TrackPlanModule.MULTI_VIEW_LICENSE_KEYS.any(license::isUnlocked)) TrackPlanModule.PRO_VISIBLE_PLANS
        else TrackPlanModule.FREE_VISIBLE_PLANS

    private val _turnouts = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    override val turnouts: StateFlow<Map<Int, Boolean>> = _turnouts

    private val _aspects = MutableStateFlow<Map<Int, Int>>(emptyMap())
    override val signalAspects: StateFlow<Map<Int, Int>> = _aspects

    private val _occupied = MutableStateFlow<Set<Pair<Int, Int>>>(emptySet())
    override val occupied: StateFlow<Set<Pair<Int, Int>>> = _occupied

    init {
        // Nach einem Wechsel auf ein kleineres Abo nicht mehr Plaene zeigen als erlaubt.
        repository.visible.update { ids -> ids.filter { id -> plans.value.any { it.id == id } }.take(maxVisiblePlans) }
    }

    override fun update(plan: TrackPlan) {
        repository.plans.update { list -> list.map { if (it.id == plan.id) plan else it } }
        repository.unsaved.value = true
    }

    override fun addPlan(name: String): TrackPlan {
        val plan = TrackPlan(id = repository.newId(), name = name.ifBlank { "Gleisbild" })
        repository.plans.update { it + plan }
        repository.unsaved.value = true
        return plan
    }

    override fun removePlan(id: String) {
        repository.plans.update { list -> list.filterNot { it.id == id }.ifEmpty { listOf(TrackPlan(repository.newId(), "Gleisbild 1")) } }
        repository.visible.update { ids ->
            ids.filter { it != id }.ifEmpty { listOf(repository.plans.value.first().id) }
        }
        repository.unsaved.value = true
    }

    override fun save() {
        if (!canSave) throw LicenseRequiredException("Speichern ist mit einem Abo möglich (ab Basis).")
        repository.saveAll()
    }

    override fun toggleVisible(id: String) {
        val current = repository.visible.value
        if (id in current) {
            if (current.size > 1) repository.visible.value = current - id
            return
        }
        if (maxVisiblePlans == 1) {
            repository.visible.value = listOf(id)
            return
        }
        if (current.size >= maxVisiblePlans) {
            throw LicenseRequiredException("Es lassen sich höchstens $maxVisiblePlans Gleisbilder gleichzeitig anzeigen.")
        }
        repository.visible.value = current + id
    }

    override suspend fun operate(tile: TrackTile) {
        if (tile.shape.kind == TileKind.SOUND) return setOn(tile, true)
        val address = tile.address ?: return
        when (tile.shape.kind) {
            TileKind.TURNOUT -> if (tile.shape == TrackShape.TURNOUT_THREE_WAY) {
                // gerade -> links -> rechts -> gerade
                when (tile.activeBranch(_turnouts.value) ?: Branch.RIGHT) {
                    Branch.MAIN -> {
                        station.setTurnout(address + 1, thrown = false)
                        station.setTurnout(address, thrown = true)
                    }
                    Branch.LEFT -> {
                        station.setTurnout(address, thrown = false)
                        station.setTurnout(address + 1, thrown = true)
                    }
                    Branch.RIGHT -> {
                        station.setTurnout(address, thrown = false)
                        station.setTurnout(address + 1, thrown = false)
                    }
                }
            } else {
                station.setTurnout(address, thrown = _turnouts.value[address] != true)
            }
            // Signale: zwischen Halt (Bild 0) und Fahrt (Bild 1) wechseln, weitere Bilder ueber setAspect
            TileKind.SIGNAL -> setAspect(tile, if (currentAspect(address) == 0) 1 else 0)
            // Zubehoer mit Bildern: reihum zum naechsten Bild
            TileKind.ASPECT -> setAspect(tile, (currentAspect(address) + 1) % tile.aspectNames().size)
            // Symbole: an/aus umschalten (Zustand 1 bzw. 0)
            TileKind.SYMBOL -> station.setTurnout(address, thrown = _turnouts.value[address] != true)
            TileKind.RELAY, TileKind.LIGHT, TileKind.GATE -> setOn(tile, !tile.isOn(_turnouts.value))
            TileKind.UNCOUPLER -> {
                station.setTurnout(address, thrown = true)
                delay(tile.pulseMillis() ?: 500L)
                station.setTurnout(address, thrown = false)
            }
            else -> Unit
        }
    }

    override suspend fun setOn(tile: TrackTile, on: Boolean) {
        if (on && tile.shape.kind == TileKind.SOUND) tile.prop(AccessoryProps.FILE).takeIf { it.isNotEmpty() }?.let { _sounds.tryEmit(it) }
        val address = tile.address ?: return
        station.setTurnout(address, tile.outputFor(on))
        val pulse = tile.pulseMillis() ?: return
        if (on) {
            delay(pulse)
            station.setTurnout(address, tile.outputFor(false))
        }
    }

    private val _sounds = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val sounds: SharedFlow<String> = _sounds

    private val _positions = MutableStateFlow<Map<Int, Int>>(emptyMap())
    override val tablePositions: StateFlow<Map<Int, Int>> = _positions

    private val _moving = MutableStateFlow<Set<Int>>(emptySet())
    override val tablesMoving: StateFlow<Set<Int>> = _moving
    private val movingJobs = mutableMapOf<Int, Job>()

    override suspend fun turnTo(tile: TrackTile, position: Int) {
        val base = tile.address ?: return
        val config = TableConfig.of(tile)
        require(position in 0 until config.positions) { "Position $position gibt es nicht" }
        checkUnlocked(config)
        val from = _positions.value[base]
        val out = config.output(base, position)
        station.setTurnout(out.address, out.thrown)
        moved(base, config, from, position)
    }

    override suspend fun tableCommand(tile: TrackTile, command: TableCommand) {
        val base = tile.address ?: return
        val config = TableConfig.of(tile)
        checkUnlocked(config)
        val from = _positions.value[base]
        val current = from ?: config.tracks.first()
        val target = when (command) {
            TableCommand.TURN_180 -> (current + config.positions / 2) % config.positions
            TableCommand.STEP_CW -> config.tracks.firstOrNull { it > current } ?: config.tracks.first()
            TableCommand.STEP_CCW -> config.tracks.lastOrNull { it < current } ?: config.tracks.last()
        }
        val explicit = when (command) {
            TableCommand.TURN_180 -> config.turn
            TableCommand.STEP_CW -> config.stepCw
            TableCommand.STEP_CCW -> config.stepCcw
        }
        // Ohne eigenen Befehl faehrt die Buehne das Zielgleis direkt an
        if (explicit == null) return turnTo(tile, target)
        station.setTurnout(explicit.address, explicit.thrown)
        moved(base, config, from, target)
    }

    private fun checkUnlocked(config: TableConfig) {
        val block = config.blockFeedback
        if (config.lockWhileOccupied && block != null && block in _occupied.value) {
            throw IllegalStateException("Die Bühne ist belegt und darf sich nicht bewegen.")
        }
    }

    /** Neue Position merken; die Buehne gilt als in Bewegung, bis der Fertig-Melder kommt oder die Schrittzeit um ist. */
    private fun moved(base: Int, config: TableConfig, from: Int?, to: Int) {
        _positions.update { it + (base to to) }
        movingJobs.remove(base)?.cancel()
        val steps = if (from == null) 1 else minOf((to - from).mod(config.positions), (from - to).mod(config.positions))
        when {
            from == to -> _moving.update { it - base }
            config.ready != null -> _moving.update { it + base }
            config.stepMillis > 0 -> {
                _moving.update { it + base }
                movingJobs[base] = scope.launch {
                    delay(config.stepMillis * steps)
                    _moving.update { it - base }
                }
            }
            else -> _moving.update { it - base }
        }
    }

    private fun currentAspect(base: Int): Int = _aspects.value[base] ?: if (_turnouts.value[base] == false) 1 else 0

    override suspend fun setAspect(tile: TrackTile, aspect: Int) {
        val base = tile.address ?: return
        require(aspect in 0 until SignalAddressing.MAX_ASPECTS) { "Signalbild $aspect gibt es nicht" }
        val (address, thrown) = SignalAddressing.output(base, aspect)
        station.setTurnout(address, thrown)
        _aspects.update { it + (base to aspect) }
    }

    fun onTurnout(address: Int, thrown: Boolean) {
        _turnouts.update { it + (address to thrown) }
        val tiles = repository.plans.value.flatMap { it.tiles }
        // Drehscheiben und Schiebebuehnen: Meldung einer Gleisadresse = Buehne steht an diesem Gleis
        for (t in tiles.filter { it.shape.isTable }) {
            val base = t.address ?: continue
            val config = TableConfig.of(t)
            config.positionFor(base, address, thrown)?.let { p -> _positions.update { it + (base to p) } }
        }
        // Gehoert die Adresse zu einem Signal? Dann das naechstliegende Signal mit kleinerer Grundadresse.
        val multi = setOf(TileKind.SIGNAL, TileKind.ASPECT)
        val usedElsewhere = tiles.any { t ->
            val a = t.address
            t.shape.kind !in multi && a != null && address in a until a + t.shape.addressCount
        }
        if (usedElsewhere) return
        val base = tiles.filter { it.shape.kind in multi }.mapNotNull { it.address }
            .filter { it <= address && address - it < SignalAddressing.MAX_ASPECTS / 2 }
            .maxOrNull() ?: return
        _aspects.update { it + (base to SignalAddressing.aspect(base, address, thrown)) }
    }

    fun onFeedback(module: Int, contact: Int, occupied: Boolean) {
        val ref = module to contact
        val changed = (ref in _occupied.value) != occupied
        _occupied.update { if (occupied) it + ref else it - ref }
        if (changed) automate(ref, occupied)
    }

    /**
     * Steuerung wie in iTrain: Einschalt-Melder schalten Zubehoer ein, sobald sie belegt werden (z. B. Bahnuebergang
     * schliessen, Sound abspielen), Ausschalt-Melder wieder aus, sobald der Zug sie verlassen hat. Ohne Ausschalt-Melder geht es aus, sobald alle Einschalt-Melder frei sind.
     * Der Fertig-Melder einer Drehscheibe beendet ihre Bewegung.
     */
    private fun automate(ref: Pair<Int, Int>, occupied: Boolean) {
        for (t in repository.plans.value.flatMap { it.tiles }) {
            if (t.shape.isTable) {
                val base = t.address ?: continue
                if (occupied && TableConfig.of(t).ready == ref) {
                    movingJobs.remove(base)?.cancel()
                    _moving.update { it - base }
                }
                continue
            }
            if (!t.shape.switchable) continue
            val on = FeedbackList.parse(t.prop(AccessoryProps.ON_FEEDBACK))
            val off = FeedbackList.parse(t.prop(AccessoryProps.OFF_FEEDBACK))
            val switchOn = occupied && ref in on
            val switchOff = when {
                t.shape.kind == TileKind.SOUND -> false
                off.isNotEmpty() -> !occupied && ref in off
                else -> !occupied && ref in on && on.none { it in _occupied.value }
            }
            if (switchOn || switchOff) scope.launch { setOn(t, switchOn) }
        }
    }
}
