package de.moba.modules.driving

import de.moba.core.runtime.LicenseInfo
import de.moba.core.runtime.MobaModule
import de.moba.core.runtime.ModuleContext
import de.moba.core.runtime.SemVer
import de.moba.core.runtime.on
import de.moba.core.runtime.provide
import de.moba.core.station.CommandStation
import de.moba.core.station.Direction
import de.moba.core.station.LocoState
import de.moba.core.station.StationEvent
import de.moba.modules.driving.api.DRIVING_MODULE_ID
import de.moba.modules.driving.api.DrivingService
import de.moba.modules.driving.api.LocoLimitReachedException
import de.moba.core.model.LocoDynamics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Modul "Fahren" (M1): Loks fahren und Funktionen schalten. Kostenlos bis [FREE_LOCO_LIMIT] Loks. */
class DrivingModule : MobaModule {
    override val id = DRIVING_MODULE_ID
    override val displayName = "Fahren"
    override val version = SemVer(1, 0)
    override val apiVersion = SemVer(1, 0)
    override val licenseKey: String? = null

    override suspend fun start(ctx: ModuleContext) {
        val service = DrivingServiceImpl(ctx.station, ctx.license, ctx.scope) { address ->
            ctx.layout.layout.value.locos.firstOrNull { it.address == address }?.dynamics
        }
        ctx.events.on<StationEvent.LocoChanged>(ctx.scope) { service.onLocoChanged(it.state) }
        ctx.provide<DrivingService>(service)
    }

    companion object {
        const val FREE_LOCO_LIMIT = 3
        const val UNLIMITED_LICENSE_KEY = "basis"
    }
}

internal class DrivingServiceImpl(
    private val station: CommandStation,
    license: LicenseInfo,
    private val scope: CoroutineScope,
    /** Fahreigenschaften der Lok aus der Fahrzeugverwaltung; `null` = keine eingetragen. */
    private val dynamics: (Int) -> LocoDynamics? = { null },
) : DrivingService {
    private val _locos = MutableStateFlow<Map<Int, LocoState>>(emptyMap())
    override val locos: StateFlow<Map<Int, LocoState>> = _locos

    override val locoLimit: Int? =
        if (license.isUnlocked(DrivingModule.UNLIMITED_LICENSE_KEY)) null else DrivingModule.FREE_LOCO_LIMIT

    private val controlled = mutableSetOf<Int>()

    // Traegheitssimulation: Ziel je Lok, zuletzt gesendete Fahrstufe und die laufende Rampe
    private class Goal(val speed: Int, val direction: Direction)
    private val goals = mutableMapOf<Int, Goal>()
    private val sent = mutableMapOf<Int, Pair<Int, Direction>>()
    private val ramps = mutableMapOf<Int, Job>()
    private val lock = Mutex()
    private val _targets = MutableStateFlow<Map<Int, Int>>(emptyMap())
    override val targets: StateFlow<Map<Int, Int>> = _targets

    override suspend fun drive(address: Int, speed: Int, direction: Direction) {
        claim(address)
        val d = dynamics(address)?.takeIf { it.inertia }
        if (d == null) {
            cancelRamp(address)
            send(address, speed, direction)
            return
        }
        lock.withLock {
            goals[address] = Goal(speed.coerceIn(0, MAX_STEP), direction)
            _targets.update { it + (address to speed.coerceIn(0, MAX_STEP)) }
            if (ramps[address]?.isActive != true) ramps[address] = scope.launch { ramp(address) }
        }
    }

    override suspend fun setFunction(address: Int, function: Int, on: Boolean) {
        claim(address)
        station.setLocoFunction(address, function, on)
    }

    override suspend fun stop(address: Int) {
        val direction = sent[address]?.second ?: _locos.value[address]?.direction ?: Direction.FORWARD
        if (dynamics(address)?.inertia == true && (sent[address]?.first ?: _locos.value[address]?.speed ?: 0) > 0) {
            claim(address)
            drive(address, 0, direction)
        } else {
            cancelRamp(address)
            send(address, 0, direction)
        }
    }

    override suspend fun emergencyBrake(address: Int) {
        val direction = sent[address]?.second ?: _locos.value[address]?.direction ?: Direction.FORWARD
        cancelRamp(address)
        send(address, 0, direction)
    }

    /**
     * Faehrt die Fahrstufe schrittweise aufs Ziel: alle [LocoDynamics.periodMs] ein Schritt, so gross, dass
     * 0 bis Hoechststufe die Anfahr- bzw. Bremszeit dauert. Ein Richtungswechsel bremst erst bis zum Stillstand.
     */
    private suspend fun ramp(address: Int) {
        try {
            while (true) {
                val goal = lock.withLock { goals[address] } ?: break
                val d = (dynamics(address) ?: LocoDynamics(inertia = true)).validated()
                val (current, currentDirection) = sent[address]
                    ?: _locos.value[address]?.let { it.speed to it.direction }
                    ?: (0 to goal.direction)
                if (current == 0 && currentDirection != goal.direction) {
                    send(address, 0, goal.direction)
                    continue
                }
                val aim = if (currentDirection != goal.direction) 0 else goal.speed
                if (current == aim && currentDirection == goal.direction) break
                val seconds = if (aim > current) d.accelerationSeconds else d.brakingSeconds
                val step = if (seconds <= 0.0) MAX_STEP else max(1, (MAX_STEP * d.periodMs / (seconds * 1000)).roundToInt())
                val next = if (aim > current) min(aim, current + step) else max(aim, current - step)
                send(address, next, currentDirection)
                if (next == goal.speed && currentDirection == goal.direction) break
                delay(d.periodMs.toLong())
            }
        } finally {
            val self = currentCoroutineContext()[Job]
            withContext(NonCancellable) {
                lock.withLock {
                    if (ramps[address] == self) {
                        ramps.remove(address)
                        goals.remove(address)
                        _targets.update { it - address }
                    }
                }
            }
        }
    }

    private suspend fun cancelRamp(address: Int) {
        val job = lock.withLock {
            goals.remove(address)
            _targets.update { it - address }
            ramps.remove(address)
        }
        job?.cancelAndJoin()
    }

    private suspend fun send(address: Int, speed: Int, direction: Direction) {
        station.setLocoSpeed(address, speed, direction)
        sent[address] = speed to direction
    }

    fun onLocoChanged(state: LocoState) {
        // Auch Loks, die ein Handregler an der Zentrale faehrt, werden angezeigt.
        _locos.update { it + (state.address to state) }
        // Ohne laufende Rampe gilt, was die Zentrale meldet (z. B. Handregler)
        if (ramps[state.address]?.isActive != true) sent[state.address] = state.speed to state.direction
    }

    private fun claim(address: Int) {
        val limit = locoLimit
        if (limit != null && address !in controlled && controlled.size >= limit) throw LocoLimitReachedException(limit)
        controlled += address
    }

    private companion object {
        const val MAX_STEP = 126
    }
}
