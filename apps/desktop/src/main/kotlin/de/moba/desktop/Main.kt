package de.moba.desktop

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.moba.adapters.catalog.StationCatalog
import de.moba.modules.decoder.DecoderModule
import de.moba.modules.decoder.DecoderRepository
import de.moba.modules.decoder.FileDecoderStore
import de.moba.modules.decoder.ui.DecoderScreen
import de.moba.modules.driving.DrivingModule
import de.moba.modules.driving.ui.DrivingPanel
import de.moba.modules.driving.ui.DrivingScreen
import de.moba.modules.trackplan.FileTrackPlanStore
import de.moba.modules.trackplan.TrackPlanModule
import de.moba.modules.trackplan.TrackPlanRepository
import de.moba.modules.trackplan.ui.TrackPlanScreen
import de.moba.modules.vehicles.FileVehicleStore
import de.moba.modules.vehicles.VehicleModule
import de.moba.modules.vehicles.VehicleRepository
import de.moba.modules.vehicles.ui.VehiclesScreen
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.MobaShell
import de.moba.ui.shell.SettingsStore
import java.io.File
import java.util.prefs.Preferences

/** Einstellungen in der Windows-Registry (Java Preferences). */
private object DesktopSettings : SettingsStore {
    private val prefs = Preferences.userRoot().node("de/moba/steuerung")
    override fun get(key: String): String? = prefs.get(key, null)
    override fun set(key: String, value: String) = prefs.put(key, value)
}

/** Gleisbilder und Fahrzeuge liegen im Benutzerordner, z. B. C:\Users\<Name>\.modellbahn-steuerung. */
private val dataDir = File(System.getProperty("user.home"), ".modellbahn-steuerung")
private val trackPlans = TrackPlanRepository(FileTrackPlanStore(File(dataDir, "gleisbilder.txt")))
private val vehicles = VehicleRepository(FileVehicleStore(File(dataDir, "fahrzeuge.txt")))
private val decoders = DecoderRepository(FileDecoderStore(File(dataDir, "decoder.txt")))

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Modellbahn-Steuerung",
        state = rememberWindowState(width = 1360.dp, height = 860.dp),
    ) {
        val scope = rememberCoroutineScope()
        val app = remember {
            MobaApp(
                scope = scope,
                drivers = StationCatalog.drivers,
                discovery = StationCatalog.discovery,
                moduleFactory = { listOf(DrivingModule(), TrackPlanModule(trackPlans), VehicleModule(vehicles), DecoderModule(decoders)) },
                settings = DesktopSettings,
            ).also { it.start() }
        }
        MobaShell(app, screens = listOf(DrivingScreen, VehiclesScreen, DecoderScreen, TrackPlanScreen), panels = listOf(DrivingPanel))
    }
}
