package de.moba.modules.calibration

import de.moba.core.runtime.MobaModule
import de.moba.core.runtime.ModuleContext
import de.moba.core.runtime.SemVer
import de.moba.core.runtime.on
import de.moba.core.runtime.provide
import de.moba.core.station.CommandStation
import de.moba.core.station.Direction
import de.moba.core.station.StationEvent
import de.moba.modules.calibration.api.CALIBRATION_MODULE_ID
import de.moba.modules.calibration.api.CalibrationProgress
import de.moba.modules.calibration.api.CalibrationService
import de.moba.modules.calibration.api.CalibrationSetup
import de.moba.modules.calibration.api.CalibrationStore
import de.moba.modules.calibration.api.Contact
import de.moba.modules.calibration.api.SpeedMath
import de.moba.modules.calibration.api.SpeedPoint
import de.moba.modules.calibration.api.SpeedProfile
import de.moba.modules.calibration.api.SpeedStepMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.ComparableTimeMark
import kotlin.time.TimeSource

/** Haelt die Messergebnisse ueber Zentralenwechsel hinweg und speichert jede Aenderung. */
class CalibrationRepository(private val store: CalibrationStore?) {
    internal val profiles = MutableStateFlow(runCatching { store?.load() }.getOrNull().orEmpty())

    internal fun change(transform: (List<SpeedProfile>) -> List<SpeedProfile>) {
        val next = transform(profiles.value)
        runCatching { store?.save(next) }
        profiles.value = next
    }
}

/**
 * Modul "Loks einmessen": misst je Fahrstufe die Vorbildgeschwindigkeit einer Lok
 * (14, 28 oder 128 Fahrstufen) auf einer Messstrecke zwischen zwei Rueckmeldern. Ab Pro.
 */
class CalibrationModule(
    private val repository: CalibrationRepository,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
    private val today: () -> String = { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString() },
) : MobaModule {
    override val id = CALIBRATION_MODULE_ID
    override val displayName = "Loks einmessen"
    override val version = SemVer(1, 0)
    override val apiVersion = SemVer(1, 0)
    override val licenseKey: String = LICENSE_KEY

    private var service: CalibrationServiceImpl? = null

    override suspend fun start(ctx: ModuleContext) {
        val impl = CalibrationServiceImpl(repository, ctx.station, ctx.scope, timeSource, today)
        ctx.events.on<StationEvent.FeedbackChanged>(ctx.scope) { impl.onFeedback(it.module, it.contact, it.occupied) }
        ctx.provide<CalibrationService>(impl)
        service = impl
    }

    override suspend fun stop() {
        service?.cancel()
    }

    companion object {
        /** Ab Pro (Club enthaelt Pro). */
        const val LICENSE_KEY = "pro"
    }
}

internal class CalibrationServiceImpl(
    private val repository: CalibrationRepository,
    private val station: CommandStation,
    private val scope: CoroutineScope,
    private val timeSource: TimeSource.WithComparableMarks,
    private val today: () -> String,
) : CalibrationService {
    override val profiles: StateFlow<List<SpeedProfile>> = repository.profiles

    private val _progress = MutableStateFlow<CalibrationProgress>(CalibrationProgress.Idle)
    override val progress: StateFlow<CalibrationProgress> = _progress

    /** Rueckmelder, die gerade belegt wurden, mit Zeitpunkt des Eingangs. */
    private val triggers = MutableSharedFlow<Contact>(extraBufferCapacity = 64)
    private val marks = mutableMapOf<Contact, ComparableTimeMark>()
    private var job: Job? = null

    fun onFeedback(module: Int, contact: Int, occupied: Boolean) {
        if (!occupied) return
        val c = Contact(module, contact)
        marks[c] = timeSource.markNow()
        triggers.tryEmit(c)
    }

    override fun start(setup: CalibrationSetup) {
        require(setup.address in 1..10239) { "Die Adresse muss zwischen 1 und 10239 liegen." }
        require(setup.track.lengthMm > 0) { "Bitte die Länge der Messstrecke in mm eingeben." }
        require(setup.track.start != setup.track.end) { "Start- und Endmelder müssen verschieden sein." }
        job?.cancel()
        job = scope.launch { run(setup) }
    }

    override fun cancel() {
        job?.cancel()
        job = null
    }

    override fun delete(address: Int, mode: SpeedStepMode) =
        repository.change { list -> list.filterNot { it.address == address && it.mode == mode } }

    private suspend fun run(setup: CalibrationSetup) {
        val steps = setup.mode.measurePoints(setup.stride)
        val direction = if (setup.forward) Direction.FORWARD else Direction.REVERSE
        val points = mutableListOf<SpeedPoint>()
        fun report(index: Int, step: Int, phase: String) {
            _progress.value = CalibrationProgress.Running(setup, index, steps.size, step, phase, points.toList())
        }
        try {
            for ((index, step) in steps.withIndex()) {
                report(index, step, "Lok fährt an")
                station.setLocoSpeed(setup.address, setup.mode.toStationSpeed(step), direction)
                delay(setup.settleSeconds * 1000L)
                val kmh = measure(setup) { phase -> report(index, step, phase) }
                points += SpeedPoint(step, kmh)
                // Ist schon eine Stufe zu langsam fuer die Messstrecke, sind es die naechsten nicht unbedingt: weiter.
            }
            val profile = SpeedProfile(setup.address, setup.name, setup.mode, setup.track.scale, points.toList(), today())
            repository.change { list -> list.filterNot { it.address == profile.address && it.mode == profile.mode } + profile }
            _progress.value = CalibrationProgress.Finished(profile)
        } catch (e: CancellationException) {
            _progress.value = CalibrationProgress.Stopped("Abgebrochen", points.toList())
            throw e
        } catch (e: Exception) {
            _progress.value = CalibrationProgress.Stopped(e.message ?: "Fehler beim Einmessen", points.toList())
        } finally {
            withContext(NonCancellable) { runCatching { station.setLocoSpeed(setup.address, 0, direction) } }
        }
    }

    /** Wartet auf Start- und Endmelder und rechnet die Fahrzeit um; `null`, wenn ein Melder zu lange ausbleibt. */
    private suspend fun measure(setup: CalibrationSetup, phase: (String) -> Unit): Double? {
        val timeout = setup.timeoutSeconds * 1000L
        phase("Wartet auf Startmelder ${setup.track.start}")
        withTimeoutOrNull(timeout) { triggers.first { it == setup.track.start } } ?: return null
        val begin = marks.getValue(setup.track.start)
        phase("Misst bis Endmelder ${setup.track.end}")
        withTimeoutOrNull(timeout) { triggers.first { it == setup.track.end } } ?: return null
        val millis = (marks.getValue(setup.track.end) - begin).inWholeMilliseconds
        if (millis <= 0) return null
        return SpeedMath.scaleKmh(setup.track.lengthMm, millis, setup.track.scale)
    }
}
