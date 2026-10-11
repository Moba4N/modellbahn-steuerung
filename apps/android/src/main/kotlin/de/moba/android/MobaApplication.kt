package de.moba.android

import android.app.Application
import android.content.Context
import de.moba.adapters.simulator.SimulatorStation
import de.moba.adapters.z21.z21Station
import de.moba.modules.driving.DrivingModule
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.SettingsStore
import de.moba.ui.shell.StationConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Haelt die Verbindung zur Zentrale ueber Bildschirmdrehungen hinweg. */
class MobaApplication : Application() {
    val app: MobaApp by lazy {
        val prefs = getSharedPreferences("moba", Context.MODE_PRIVATE)
        MobaApp(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            stationFactory = { config ->
                when (config) {
                    StationConfig.Simulator -> SimulatorStation()
                    is StationConfig.Z21 -> z21Station(config.host)
                }
            },
            moduleFactory = { listOf(DrivingModule()) },
            settings = object : SettingsStore {
                override fun get(key: String): String? = prefs.getString(key, null)
                override fun set(key: String, value: String) = prefs.edit().putString(key, value).apply()
            },
        ).also { it.start() }
    }
}
