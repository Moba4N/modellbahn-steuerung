package de.moba.android

import android.app.Application
import android.content.Context
import de.moba.adapters.catalog.StationCatalog
import de.moba.modules.driving.DrivingModule
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Haelt die Verbindung zur Zentrale ueber Bildschirmdrehungen hinweg. */
class MobaApplication : Application() {
    val app: MobaApp by lazy {
        val prefs = getSharedPreferences("moba", Context.MODE_PRIVATE)
        MobaApp(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            drivers = StationCatalog.drivers,
            discovery = StationCatalog.discovery,
            moduleFactory = { listOf(DrivingModule()) },
            settings = object : SettingsStore {
                override fun get(key: String): String? = prefs.getString(key, null)
                override fun set(key: String, value: String) = prefs.edit().putString(key, value).apply()
            },
        ).also { it.start() }
    }
}
