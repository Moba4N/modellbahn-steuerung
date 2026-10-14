package de.moba.android

import android.app.Application
import android.content.Context
import de.moba.adapters.catalog.StationCatalog
import de.moba.modules.decoder.DecoderModule
import de.moba.modules.decoder.DecoderRepository
import de.moba.modules.decoder.FileDecoderStore
import de.moba.modules.driving.DrivingModule
import de.moba.modules.trackplan.FileTrackPlanStore
import de.moba.modules.trackplan.TrackPlanModule
import de.moba.modules.trackplan.TrackPlanRepository
import de.moba.modules.symbols.FileSymbolStore
import de.moba.modules.symbols.SymbolModule
import de.moba.modules.symbols.SymbolRepository
import de.moba.modules.vehicles.FileVehicleStore
import de.moba.modules.vehicles.VehicleModule
import de.moba.modules.vehicles.VehicleRepository
import de.moba.core.runtime.EcLicenseVerifier
import de.moba.core.runtime.ManufacturerKey
import de.moba.modules.throttles.FileThrottleBarStore
import de.moba.modules.throttles.ThrottleBarModule
import de.moba.modules.throttles.ThrottleBarRepository
import de.moba.ui.shell.ImageStore
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
        val symbols = SymbolRepository(FileSymbolStore(File(filesDir, "symbole.txt")))
        val decoders = DecoderRepository(FileDecoderStore(File(filesDir, "decoder.txt")))
        val throttleBar = ThrottleBarRepository(FileThrottleBarStore(File(filesDir, "fahrregler.txt")))
        MobaApp(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            drivers = StationCatalog.drivers,
            discovery = StationCatalog.discovery,
            moduleFactory = { listOf(DrivingModule(), TrackPlanModule(trackPlans), VehicleModule(vehicles), DecoderModule(decoders), SymbolModule(symbols), ThrottleBarModule(throttleBar)) },
            settings = object : SettingsStore {
                override fun get(key: String): String? = prefs.getString(key, null)
                override fun set(key: String, value: String) = prefs.edit().putString(key, value).apply()
            },
            imageStore = object : ImageStore {
                private val file = File(filesDir, "hintergrund.bild")
                override fun load(): ByteArray? = file.takeIf { it.isFile }?.readBytes()
                override fun save(bytes: ByteArray?) {
                    if (bytes == null) file.delete() else file.writeBytes(bytes)
                }
            },
            licenseVerifier = EcLicenseVerifier(ManufacturerKey.PUBLIC),
        ).also { it.start() }
    }
}
