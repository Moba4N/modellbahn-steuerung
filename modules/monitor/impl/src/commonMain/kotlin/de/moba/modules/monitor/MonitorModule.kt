package de.moba.modules.monitor

import de.moba.core.runtime.MobaModule
import de.moba.core.runtime.ModuleContext
import de.moba.core.runtime.SemVer
import de.moba.core.runtime.on
import de.moba.core.runtime.provide
import de.moba.core.station.StationEvent
import de.moba.core.station.TrafficSource
import de.moba.core.station.describe
import de.moba.core.station.toHex
import de.moba.modules.monitor.api.MONITOR_MODULE_ID
import de.moba.modules.monitor.api.MonitorEntry
import de.moba.modules.monitor.api.MonitorKind
import de.moba.modules.monitor.api.MonitorService
import de.moba.modules.monitor.api.formatMonitorTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/** Zusatzmodul "Monitor": schneidet den Datenverkehr zur Zentrale mit. Kostenlos, wird ueber das Menue geoeffnet. */
class MonitorModule(private val timeSource: TimeSource = TimeSource.Monotonic) : MobaModule {
    override val id = MONITOR_MODULE_ID
    override val displayName = "Monitor"
    override val version = SemVer(1, 0)
    override val apiVersion = SemVer(1, 0)
    override val licenseKey: String? = null

    override suspend fun start(ctx: ModuleContext) {
        val traffic = ctx.station as? TrafficSource
        val service = MonitorServiceImpl(timeSource, rawAvailable = traffic != null)
        traffic?.let { source ->
            ctx.scope.launch {
                source.traffic.collect { p ->
                    service.add(if (p.outgoing) MonitorKind.SENT else MonitorKind.RECEIVED, p.bytes.toHex(), p.description)
                }
            }
        }
        ctx.events.on<StationEvent>(ctx.scope) { service.add(MonitorKind.EVENT, "", it.describe()) }
        ctx.provide<MonitorService>(service)
    }

    override suspend fun stop() {}
}

internal class MonitorServiceImpl(timeSource: TimeSource, override val rawAvailable: Boolean) : MonitorService {
    private val start = timeSource.markNow()
    private var seq = 0L
    private val _entries = MutableStateFlow<List<MonitorEntry>>(emptyList())
    override val entries: StateFlow<List<MonitorEntry>> = _entries
    private val _paused = MutableStateFlow(false)
    override val paused: StateFlow<Boolean> = _paused

    fun add(kind: MonitorKind, hex: String, text: String?) {
        if (_paused.value) return
        val entry = MonitorEntry(seq++, start.elapsedNow().inWholeMilliseconds, kind, hex, text)
        _entries.update { (it + entry).takeLast(MonitorService.MAX_ENTRIES) }
    }

    override fun setPaused(paused: Boolean) {
        _paused.value = paused
    }

    override fun clear() {
        _entries.value = emptyList()
    }

    override fun export(kinds: Set<MonitorKind>): String = _entries.value.filter { it.kind in kinds }.joinToString("\n") { e ->
        listOf(formatMonitorTime(e.millis), e.kind.label, e.hex, e.text ?: "").filter { it.isNotEmpty() }.joinToString("  |  ")
    }
}
