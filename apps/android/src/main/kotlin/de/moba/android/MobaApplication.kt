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
import de.moba.modules.calibration.CalibrationModule
import de.moba.modules.calibration.CalibrationRepository
import de.moba.modules.calibration.FileCalibrationStore
import de.moba.modules.routes.FileRouteStore
import de.moba.modules.monitor.MonitorModule
import de.moba.modules.routes.RouteModule
import de.moba.modules.routes.RouteRepository
import de.moba.modules.throttles.FileThrottleBarStore
import de.moba.modules.throttles.ThrottleBarModule
import de.moba.modules.throttles.ThrottleBarRepository
import de.moba.core.files.FolderLayoutFiles
import de.moba.ui.shell.FileSupport
import de.moba.ui.shell.ImageStore
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Haelt die Verbindung zur Zentrale ueber Bildschirmdrehungen hinweg. */
class MobaApplication : Application() {
    private val prefs by lazy { getSharedPreferences("moba", Context.MODE_PRIVATE) }

    /** App-Speicher als Standard-Anlage, weitere Anlagen unter "anlagen" (Datei-Menue). */
    val layoutFiles by lazy {
        FolderLayoutFiles(filesDir, { prefs.getString(it, null) }, { k, v -> prefs.edit().putString(k, v).apply() })
    }

    /** Die Daten der offenen Anlage; bleiben ueber Neustarts der Module erhalten. */
    private class LayoutData(dir: File) {
        val trackPlans = TrackPlanRepository(FileTrackPlanStore(File(dir, "gleisbilder.txt")))
        val vehicles = VehicleRepository(FileVehicleStore(File(dir, "fahrzeuge.txt")))
        val symbols = SymbolRepository(FileSymbolStore(File(dir, "symbole.txt")))
        val decoders = DecoderRepository(FileDecoderStore(File(dir, "decoder.txt")))
        val throttleBar = ThrottleBarRepository(FileThrottleBarStore(File(dir, "fahrregler.txt")))
        val calibration = CalibrationRepository(FileCalibrationStore(File(dir, "einmessen.txt")))
        val routes = RouteRepository(FileRouteStore(File(dir, "fahrstrassen.txt")))
    }

    private var data: LayoutData? = null

    /** Laedt die Daten der jetzt offenen Anlage und startet die Module neu. */
    val files by lazy {
        FileSupport(layoutFiles) {
            data = LayoutData(layoutFiles.dir)
            app.reloadModules()
        }
    }

    val app: MobaApp by lazy {
        data = LayoutData(layoutFiles.dir)
        runCatching { layoutFiles.autoBackup() }
        MobaApp(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            drivers = StationCatalog.drivers,
            discovery = StationCatalog.discovery,
            moduleFactory = {
                val d = data ?: LayoutData(layoutFiles.dir).also { data = it }
                listOf(DrivingModule(), TrackPlanModule(d.trackPlans), VehicleModule(d.vehicles), DecoderModule(d.decoders), SymbolModule(d.symbols), ThrottleBarModule(d.throttleBar), CalibrationModule(d.calibration), RouteModule(d.routes), MonitorModule())
            },
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
