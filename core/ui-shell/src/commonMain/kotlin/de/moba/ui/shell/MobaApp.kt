package de.moba.ui.shell

import de.moba.core.model.InMemoryLayoutRepository
import de.moba.core.runtime.LicenseInfo
import de.moba.core.runtime.MobaModule
import de.moba.core.runtime.ModuleHost
import de.moba.core.station.CommandStation
import de.moba.core.station.FoundStation
import de.moba.core.station.StationConfig
import de.moba.core.station.StationDiscovery
import de.moba.core.station.StationDriver
import de.moba.core.station.StationEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Einfacher Einstellungsspeicher; die Apps setzen ihn auf Android-Preferences bzw. Java-Preferences um. */
interface SettingsStore {
    fun get(key: String): String?
    fun set(key: String, value: String)

    class InMemory : SettingsStore {
        private val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun set(key: String, value: String) {
            values[key] = value
        }
    }
}

/** Art einer Meldung; bestimmt Farbe und Symbol in der Liste. */
enum class LogKind { INFO, OCCUPIED, FREE, ALARM }

/** Eine Meldung der Zentrale mit Uhrzeit (HH:MM:SS). */
data class LogEntry(val time: String, val text: String, val kind: LogKind)

/** Eine Verbindung zu einer Zentrale mit den dafuer gestarteten Modulen. */
class Session(val config: StationConfig, val driver: StationDriver, val station: CommandStation, val host: ModuleHost)

/**
 * Verbindet Zentrale, Datenmodell und Modul-Host. Android- und Windows-App erzeugen je eine Instanz.
 * Beim Wechsel der Zentrale werden alle Module gestoppt und mit der neuen Zentrale neu gestartet.
 *
 * [drivers] sind alle waehlbaren Zentralen; der erste Treiber ist der Standard (in der Regel der Simulator).
 */
class MobaApp(
    private val scope: CoroutineScope,
    val drivers: List<StationDriver>,
    private val discovery: StationDiscovery? = null,
    private val moduleFactory: () -> List<MobaModule>,
    private val license: LicenseInfo = LicenseInfo.FREE,
    private val settings: SettingsStore = SettingsStore.InMemory(),
) {
    val layout = InMemoryLayoutRepository()

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session

    private val _trackPower = MutableStateFlow(false)
    val trackPower: StateFlow<Boolean> = _trackPower

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** Die letzten Meldungen der Zentrale, neueste zuerst. Zeigt z. B., ob s88-Melder ankommen. */
    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())
    val log: StateFlow<List<LogEntry>> = _log

    private val _darkTheme = MutableStateFlow(settings.get(KEY_THEME) != "light")
    val darkTheme: StateFlow<Boolean> = _darkTheme

    fun setDarkTheme(dark: Boolean) {
        _darkTheme.value = dark
        settings.set(KEY_THEME, if (dark) "dark" else "light")
    }

    private val _found = MutableStateFlow<List<FoundStation>>(emptyList())

    /** Ergebnis der letzten Netzwerksuche. */
    val found: StateFlow<List<FoundStation>> = _found

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching

    val canDiscover: Boolean get() = discovery != null

    /** Sucht Zentralen, die sich im Netz melden (Z21, Maerklin CS). */
    fun discover() = scope.launch {
        val d = discovery ?: return@launch
        if (_searching.value) return@launch
        _searching.value = true
        try {
            _found.value = runCatching { d.discover() }.getOrDefault(emptyList())
        } finally {
            _searching.value = false
        }
    }

    fun driver(id: String): StationDriver? = drivers.firstOrNull { it.id == id }

    private val switching = Mutex()
    private var eventJob: Job? = null

    /** Zuletzt gewaehlte Zentrale, sonst der erste Treiber. */
    val savedConfig: StationConfig
        get() {
            val driver = settings.get(KEY_TYPE)?.let(::driver) ?: drivers.first()
            val host = settings.get(KEY_HOST).orEmpty()
            if (driver.needsHost && host.isBlank()) return StationConfig(drivers.first().id)
            return StationConfig(
                driverId = driver.id,
                model = settings.get(KEY_MODEL)?.takeIf { it in driver.models },
                host = if (driver.needsHost) host else "",
                port = settings.get(KEY_PORT)?.toIntOrNull(),
                options = driver.options.associate { it.key to (settings.get("$KEY_OPTION${it.key}") ?: it.default) },
            )
        }

    fun start() = connect(savedConfig)

    fun connect(config: StationConfig) = scope.launch {
        switching.withLock {
            _session.value?.let { old ->
                old.host.stopAll()
                runCatching { old.station.disconnect() }
            }
            eventJob?.cancel()
            _error.value = null
            _log.value = emptyList()

            val driver = driver(config.driverId)
            if (driver == null) {
                _error.value = "Unbekannte Zentrale: ${config.driverId}"
                _session.value = null
                return@withLock
            }
            settings.set(KEY_TYPE, driver.id)
            settings.set(KEY_MODEL, config.model.orEmpty())
            if (driver.needsHost) {
                settings.set(KEY_HOST, config.host)
                settings.set(KEY_PORT, config.port?.toString().orEmpty())
            }
            config.options.forEach { (key, value) -> settings.set("$KEY_OPTION$key", value) }

            val station = driver.create(config)
            val host = ModuleHost(station, layout, license, scope).apply { moduleFactory().forEach(::register) }
            eventJob = scope.launch { station.events.collect(::onStationEvent) }
            _session.value = Session(config, driver, station, host)

            try {
                station.connect()
            } catch (e: Exception) {
                _error.value = e.message ?: "Verbindung fehlgeschlagen"
                return@withLock
            }
            host.startAll()
        }
    }

    fun setTrackPower(on: Boolean) = command { it.setTrackPower(on) }

    /** Nothalt ist nie lizenzpflichtig und immer erreichbar. */
    fun emergencyStop() = command { it.emergencyStop() }

    private fun command(block: suspend (CommandStation) -> Unit) = scope.launch {
        val station = _session.value?.station ?: return@launch
        try {
            block(station)
        } catch (e: Exception) {
            _error.value = e.message
        }
    }

    private fun onStationEvent(event: StationEvent) {
        val (text, kind) = when (event) {
            is StationEvent.ConnectionChanged -> {
                _connected.value = event.connected
                if (event.connected) "Verbunden mit ${_session.value?.station?.name}" to LogKind.INFO
                else "Verbindung getrennt" to LogKind.ALARM
            }
            is StationEvent.TrackPowerChanged -> {
                _trackPower.value = event.on
                if (event.on) "Gleisspannung an" to LogKind.INFO else "Gleisspannung aus" to LogKind.ALARM
            }
            StationEvent.EmergencyStopped -> "Nothalt" to LogKind.ALARM
            is StationEvent.FeedbackChanged ->
                "s88 ${event.module}.${event.contact} ${if (event.occupied) "belegt" else "frei"}" to
                    if (event.occupied) LogKind.OCCUPIED else LogKind.FREE
            is StationEvent.TurnoutChanged ->
                "Weiche ${event.address} ${if (event.thrown) "abzweigend" else "gerade"}" to LogKind.INFO
            is StationEvent.LocoChanged -> return // zu haeufig fuer die Liste
        }
        _log.update { (listOf(LogEntry(now(), text, kind)) + it).take(LOG_SIZE) }
    }

    private fun now(): String {
        val t = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        fun two(n: Int) = n.toString().padStart(2, '0')
        return "${two(t.hour)}:${two(t.minute)}:${two(t.second)}"
    }

    private companion object {
        const val KEY_TYPE = "station.type"
        const val KEY_HOST = "station.host"
        const val KEY_PORT = "station.port"
        const val KEY_MODEL = "station.model"
        const val KEY_OPTION = "station.option."
        const val KEY_THEME = "ui.theme"
        const val LOG_SIZE = 30
    }
}
