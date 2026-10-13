package de.moba.desktop

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
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
import de.moba.modules.symbols.FileSymbolStore
import de.moba.modules.symbols.SymbolModule
import de.moba.modules.symbols.SymbolRepository
import de.moba.modules.symbols.ui.SymbolsScreen
import de.moba.modules.vehicles.FileVehicleStore
import de.moba.modules.vehicles.VehicleModule
import de.moba.modules.vehicles.VehicleRepository
import de.moba.modules.vehicles.ui.VehiclesScreen
import de.moba.core.runtime.EcLicenseVerifier
import de.moba.core.runtime.ManufacturerKey
import de.moba.ui.shell.ImagePicker
import de.moba.ui.shell.ImageStore
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.MobaShell
import de.moba.ui.shell.SettingsStore
import java.awt.FileDialog
import org.jetbrains.skia.Image
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
private val symbols = SymbolRepository(FileSymbolStore(File(dataDir, "symbole.txt")))
private val decoders = DecoderRepository(FileDecoderStore(File(dataDir, "decoder.txt")))

/** Hintergrundbild als Kopie im Datenordner, damit es auch nach Verschieben des Originals bleibt. */
private object DesktopImageStore : ImageStore {
    private val file = File(dataDir, "hintergrund.bild")
    override fun load(): ByteArray? = file.takeIf { it.isFile }?.readBytes()
    override fun save(bytes: ByteArray?) {
        if (bytes == null) {
            file.delete()
        } else {
            dataDir.mkdirs()
            file.writeBytes(bytes)
        }
    }
}

/** Windows-Dateidialog fuer Bilder. */
private fun imagePicker(window: java.awt.Frame) = ImagePicker { onResult ->
    val dialog = FileDialog(window, "Hintergrundbild wählen", FileDialog.LOAD).apply {
        setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in setOf("png", "jpg", "jpeg", "bmp", "gif", "webp") }
        file = "*.png;*.jpg;*.jpeg;*.bmp;*.gif;*.webp"
        isVisible = true
    }
    val chosen = dialog.files.firstOrNull()
    onResult(chosen?.readBytes())
}

private val appIcon = BitmapPainter(
    Image.makeFromEncoded(object {}.javaClass.getResourceAsStream("/icon.png")!!.readBytes()).toComposeImageBitmap(),
)

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Modellbahn-Steuerung",
        icon = appIcon,
        state = rememberWindowState(width = 1360.dp, height = 860.dp),
    ) {
        val scope = rememberCoroutineScope()
        val app = remember {
            MobaApp(
                scope = scope,
                drivers = StationCatalog.drivers,
                discovery = StationCatalog.discovery,
                moduleFactory = { listOf(DrivingModule(), TrackPlanModule(trackPlans), VehicleModule(vehicles), DecoderModule(decoders), SymbolModule(symbols)) },
                settings = DesktopSettings,
                imageStore = DesktopImageStore,
                licenseVerifier = EcLicenseVerifier(ManufacturerKey.PUBLIC),
            ).also { it.start() }
        }
        val picker = remember { imagePicker(window) }
        MobaShell(
            app,
            screens = listOf(DrivingScreen, VehiclesScreen, DecoderScreen, TrackPlanScreen, SymbolsScreen),
            panels = listOf(DrivingPanel),
            imagePicker = picker,
        )
    }
}
