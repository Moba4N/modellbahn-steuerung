package de.moba.android

import android.app.Application
import android.content.Context
import de.moba.adapters.catalog.StationCatalog
import de.moba.modules.driving.DrivingModule
import de.moba.modules.trackplan.FileTrackPlanStore
import de.moba.modules.trackplan.TrackPlanModule
import de.moba.modules.trackplan.TrackPlanRepository
import de.moba.modules.vehicles.FileVehicleStore
import de.moba.modules.vehicles.VehicleModule
import de.moba.modules.vehicles.VehicleRepository
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Haelt die Verbindung zur Zentrale ueber Bildschirmdrehungen hinweg. */
class MobaApplication : Application() {
    val app: MobaApp by lazy {
        val prefs = getSharedPreferences("moba", Context.MODE_PRIVATE)
        val trackPlans = TrackPlanRepository(FileTrackPlanStore(File(filesDir, "gleisbilder.txt")))
        val vehicles = VehicleRepository(FileVehicleStore(File(filesDir, "fahrzeuge.txt")))
        MobaApp(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            drivers = StationCatalog.drivers,
            discovery = StationCatalog.discovery,
            moduleFactory = { listOf(DrivingModule(), TrackPlanModule(trackPlans), VehicleModule(vehicles)) },
            settings = object : SettingsStore {
                override fun get(key: String): String? = prefs.getString(key, null)
                override fun set(key: String, value: String) = prefs.edit().putString(key, value).apply()
            },
        ).also { it.start() }
    }
}
