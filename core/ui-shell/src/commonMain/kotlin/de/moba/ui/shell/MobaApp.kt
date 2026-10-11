package de.moba.ui.shell

import de.moba.core.model.InMemoryLayoutRepository
import de.moba.core.runtime.LicenseInfo
import de.moba.core.runtime.MobaModule
import de.moba.core.runtime.ModuleHost
import de.moba.core.station.CommandStation
import de.moba.core.station.StationEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Welche Zentrale die App ansteuert. */
sealed interface StationConfig {
    data object Simulator : StationConfig
    data class Z21(val host: String) : StationConfig
}

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

/** Eine Verbindung zu einer Zentrale mit den dafuer gestarteten Modulen. */
class Session(val config: StationConfig, val station: CommandStation, val host: ModuleHost)

/**
 * Verbindet Zentrale, Datenmodell und Modul-Host. Android- und Windows-App erzeugen je eine Instanz.
 * Beim Wechsel der Zentrale werden alle Module gestoppt und mit der neuen Zentrale neu gestartet.
 */
class MobaApp(
    private val scope: CoroutineScope,
    private val stationFactory: (StationConfig) -> CommandStation,
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
    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    private val switching = Mutex()
    private var eventJob: Job? = null

    /** Zuletzt gewaehlte Zentrale, sonst der Simulator. */
    val savedConfig: StationConfig
        get() = settings.get(KEY_HOST)?.takeIf { settings.get(KEY_TYPE) == "z21" && it.isNotBlank() }
            ?.let { StationConfig.Z21(it) } ?: StationConfig.Simulator

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

            when (config) {
                StationConfig.Simulator -> settings.set(KEY_TYPE, "simulator")
                is StationConfig.Z21 -> {
                    settings.set(KEY_TYPE, "z21")
                    settings.set(KEY_HOST, config.host)
                }
            }

            val station = stationFactory(config)
            val host = ModuleHost(station, layout, license, scope).apply { moduleFactory().forEach(::register) }
            eventJob = scope.launch { station.events.collect(::onStationEvent) }
            _session.value = Session(config, station, host)

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
        val text = when (event) {
            is StationEvent.ConnectionChanged -> {
                _connected.value = event.connected
                if (event.connected) "Verbunden" else "Verbindung getrennt"
            }
            is StationEvent.TrackPowerChanged -> {
                _trackPower.value = event.on
                if (event.on) "Gleisspannung an" else "Gleisspannung aus"
            }
            StationEvent.EmergencyStopped -> "Nothalt"
            is StationEvent.FeedbackChanged ->
                "s88 Modul ${event.module}, Kontakt ${event.contact}: ${if (event.occupied) "belegt" else "frei"}"
            is StationEvent.TurnoutChanged -> "Weiche ${event.address}: ${if (event.thrown) "abzweigend" else "gerade"}"
            is StationEvent.LocoChanged -> null // zu haeufig fuer die Liste
        } ?: return
        _log.update { (listOf(text) + it).take(LOG_SIZE) }
    }

    private companion object {
        const val KEY_TYPE = "station.type"
        const val KEY_HOST = "station.host"
        const val LOG_SIZE = 30
    }
}
