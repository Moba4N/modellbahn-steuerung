package de.moba.desktop

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.moba.adapters.simulator.SimulatorStation
import de.moba.adapters.z21.z21Station
import de.moba.modules.driving.DrivingModule
import de.moba.modules.driving.ui.DrivingScreen
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.MobaShell
import de.moba.ui.shell.SettingsStore
import de.moba.ui.shell.StationConfig
import java.util.prefs.Preferences

/** Einstellungen in der Windows-Registry (Java Preferences). */
private object DesktopSettings : SettingsStore {
    private val prefs = Preferences.userRoot().node("de/moba/steuerung")
    override fun get(key: String): String? = prefs.get(key, null)
    override fun set(key: String, value: String) = prefs.put(key, value)
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Modellbahn-Steuerung",
        state = rememberWindowState(width = 900.dp, height = 800.dp),
    ) {
        val scope = rememberCoroutineScope()
        val app = remember {
            MobaApp(
                scope = scope,
                stationFactory = { config ->
                    when (config) {
                        StationConfig.Simulator -> SimulatorStation()
                        is StationConfig.Z21 -> z21Station(config.host)
                    }
                },
                moduleFactory = { listOf(DrivingModule()) },
                settings = DesktopSettings,
            ).also { it.start() }
        }
        MobaShell(app, screens = listOf(DrivingScreen))
    }
}
