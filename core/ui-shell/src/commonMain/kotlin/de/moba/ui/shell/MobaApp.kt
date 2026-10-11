package de.moba.ui.shell

import de.moba.adapters.simulator.SimulatorStation
import de.moba.core.model.InMemoryLayoutRepository
import de.moba.core.runtime.LicenseInfo
import de.moba.core.runtime.MobaModule
import de.moba.core.runtime.ModuleHost
import de.moba.core.station.CommandStation
import de.moba.core.station.StationEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Verbindet Zentrale, Datenmodell und Modul-Host. Android- und Windows-App erzeugen je eine Instanz.
 * In Phase 0 laeuft immer der Simulator; die Auswahl der echten Zentrale folgt mit dem Z21-Adapter.
 */
class MobaApp(
    private val scope: CoroutineScope,
    modules: List<MobaModule> = emptyList(),
    val station: CommandStation = SimulatorStation(),
    license: LicenseInfo = LicenseInfo.FREE,
) {
    val layout = InMemoryLayoutRepository()
    val host = ModuleHost(station, layout, license, scope).apply { modules.forEach(::register) }

    private val _trackPower = MutableStateFlow(false)
    val trackPower: StateFlow<Boolean> = _trackPower

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    fun start() = scope.launch {
        launch {
            station.events.collect {
                when (it) {
                    is StationEvent.TrackPowerChanged -> _trackPower.value = it.on
                    is StationEvent.ConnectionChanged -> _connected.value = it.connected
                    else -> Unit
                }
            }
        }
        station.connect()
        host.startAll()
    }

    fun setTrackPower(on: Boolean) = scope.launch { station.setTrackPower(on) }

    /** Nothalt ist nie lizenzpflichtig und immer erreichbar. */
    fun emergencyStop() = scope.launch {
        station.emergencyStop()
        station.setTrackPower(false)
    }
}
