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
import de.moba.modules.trackplan.FileLayoutObjectStore
import de.moba.modules.trackplan.FileTrackPlanStore
import de.moba.modules.trackplan.TrackPlanModule
import de.moba.modules.trackplan.TrackPlanRepository
import de.moba.modules.trackplan.ui.TrackPlanScreen
import de.moba.modules.trackplan.ui.TrackPlanSounds
import de.moba.modules.trackplan.ui.TrackPlanTrains
import de.moba.modules.trackplan.ui.TrackPlanWindows
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
import de.moba.modules.calibration.CalibrationModule
import de.moba.modules.calibration.CalibrationRepository
import de.moba.modules.calibration.FileCalibrationStore
import de.moba.modules.calibration.ui.CalibrationScreen
import de.moba.modules.routes.FileRouteStore
import de.moba.modules.monitor.MonitorModule
import de.moba.modules.actions.ActionModule
import de.moba.modules.actions.ActionRepository
import de.moba.modules.actions.FileActionStore
import de.moba.modules.routes.RouteModule
import de.moba.modules.routes.RouteRepository
import de.moba.modules.routes.ui.RoutesScreen
import de.moba.modules.monitor.ui.MonitorTool
import de.moba.modules.actions.ui.ActionsEditor
import de.moba.modules.actions.ui.ActionsMenu
import de.moba.modules.routes.ui.RouteEditors
import de.moba.modules.trackplan.ui.TrackObjectEditors
import de.moba.modules.vehicles.ui.VehicleEditors
import de.moba.modules.throttles.FileThrottleBarStore
import de.moba.modules.throttles.ThrottleBarModule
import de.moba.modules.throttles.ThrottleBarRepository
import de.moba.modules.throttles.ui.ThrottleDock
import de.moba.ui.shell.ExitGuard
import de.moba.ui.shell.FileSupport
import de.moba.core.files.FolderLayoutFiles
import de.moba.ui.shell.ImagePicker
import de.moba.ui.shell.ImageStore
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.MobaShell
import de.moba.ui.shell.ModuleWindowsHost
import de.moba.ui.shell.SettingsStore
import de.moba.ui.shell.SoundSupport
import de.moba.ui.shell.soundFileName
import java.awt.FileDialog
import org.jetbrains.skia.Image
import java.io.File
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineEvent
import java.util.prefs.Preferences

/** Einstellungen in der Windows-Registry (Java Preferences). */
private object DesktopSettings : SettingsStore {
    private val prefs = Preferences.userRoot().node("de/moba/steuerung")
    override fun get(key: String): String? = prefs.get(key, null)
    override fun set(key: String, value: String) = prefs.put(key, value)
}

/**
 * App-Ordner im Benutzerordner, z. B. C:\Users\<Name>\.modellbahn-steuerung. Er ist zugleich die Standard-Anlage;
 * weitere Anlagen liegen unter "anlagen" oder in einem frei gewaehlten Ordner (Datei-Menue).
 */
private val dataDir = File(System.getProperty("user.home"), ".modellbahn-steuerung")
private val layoutFiles = FolderLayoutFiles(dataDir, DesktopSettings::get, DesktopSettings::set)

/** Die Daten der offenen Anlage; bleiben ueber Neustarts der Module (z. B. Zentralenwechsel) erhalten. */
private class LayoutData(dir: File) {
    val trackPlans = TrackPlanRepository(FileTrackPlanStore(File(dir, "gleisbilder.txt")), FileLayoutObjectStore(File(dir, "anlagenobjekte.txt")))
    val vehicles = VehicleRepository(FileVehicleStore(File(dir, "fahrzeuge.txt")))
    val symbols = SymbolRepository(FileSymbolStore(File(dir, "symbole.txt")))
    val decoders = DecoderRepository(FileDecoderStore(File(dir, "decoder.txt")))
    val throttleBar = ThrottleBarRepository(FileThrottleBarStore(File(dir, "fahrregler.txt")))
    val calibration = CalibrationRepository(FileCalibrationStore(File(dir, "einmessen.txt")))
    val routes = RouteRepository(FileRouteStore(File(dir, "fahrstrassen.txt")))
    val actions = ActionRepository(FileActionStore(File(dir, "aktionen.txt")))
}

private var data = LayoutData(layoutFiles.dir)

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

/** Klaenge fuer Sound-Zubehoer: Dateidialog, Kopie in "klaenge" im Datenordner, Wiedergabe mit Java Sound. */
private class DesktopSound(private val window: java.awt.Frame) : SoundSupport {
    private val dir get() = File(layoutFiles.dir, "klaenge")
    override val formats = "WAV, AIFF oder AU."

    override fun pick(onResult: (String?) -> Unit) {
        val dialog = FileDialog(window, "Klangdatei wählen", FileDialog.LOAD).apply {
            setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in setOf("wav", "aiff", "aif", "au") }
            file = "*.wav;*.aiff;*.aif;*.au"
            isVisible = true
        }
        val chosen = dialog.files.firstOrNull() ?: return onResult(null)
        onResult(
            runCatching {
                dir.mkdirs()
                val name = soundFileName(chosen.name)
                chosen.copyTo(File(dir, name), overwrite = true)
                name
            }.getOrNull(),
        )
    }

    override fun play(name: String) {
        val file = File(dir, soundFileName(name)).takeIf { it.isFile } ?: return
        runCatching {
            val clip = AudioSystem.getClip()
            clip.addLineListener { if (it.type == LineEvent.Type.STOP) clip.close() }
            clip.open(AudioSystem.getAudioInputStream(file))
            clip.start()
        }
    }
}

private val appIcon = BitmapPainter(
    Image.makeFromEncoded(object {}.javaClass.getResourceAsStream("/icon.png")!!.readBytes()).toComposeImageBitmap(),
)

fun main() = application {
    // Schliessen fragt nach, wenn noch Gleisbilder ungespeichert sind.
    val exitGuard = remember { ExitGuard() }
    val scope = rememberCoroutineScope()
    // Die App lebt neben allen Fenstern, damit Gleisbilder auch auf weiteren Bildschirmen laufen
    val app = remember {
        MobaApp(
            scope = scope,
            drivers = StationCatalog.drivers,
            discovery = StationCatalog.discovery,
            moduleFactory = {
                val d = data
                listOf(DrivingModule(), TrackPlanModule(d.trackPlans), VehicleModule(d.vehicles), DecoderModule(d.decoders), SymbolModule(d.symbols), ThrottleBarModule(d.throttleBar), CalibrationModule(d.calibration), RouteModule(d.routes), MonitorModule(), ActionModule(d.actions))
            },
            settings = DesktopSettings,
            imageStore = DesktopImageStore,
            licenseVerifier = EcLicenseVerifier(ManufacturerKey.PUBLIC),
        ).also {
            // Einmal am Tag beim Start automatisch sichern
            runCatching { layoutFiles.autoBackup() }
            it.start()
        }
    }
    val files = remember {
        FileSupport(layoutFiles) {
            data = LayoutData(layoutFiles.dir)
            app.reloadModules()
        }
    }
    Window(
        onCloseRequest = exitGuard::request,
        title = "Modellbahn-Steuerung",
        icon = appIcon,
        state = rememberWindowState(width = 1360.dp, height = 860.dp),
    ) {
        val picker = remember { imagePicker(window) }
        val sound = remember { DesktopSound(window) }
        MobaShell(
            app,
            screens = listOf(DrivingScreen, VehiclesScreen, DecoderScreen, TrackPlanScreen, SymbolsScreen, CalibrationScreen, RoutesScreen),
            panels = listOf(DrivingPanel),
            docks = listOf(ThrottleDock),
            tools = listOf(MonitorTool),
            imagePicker = picker,
            exitGuard = exitGuard,
            onExit = ::exitApplication,
            separateWindows = true,
            sound = sound,
            effects = listOf(TrackPlanSounds, TrackPlanTrains),
            files = files,
            menus = listOf(ActionsMenu),
            editors = VehicleEditors + RouteEditors + TrackObjectEditors + ActionsEditor,
        )
    }
    // Gleisbilder in eigenen Fenstern (Pro), z. B. auf einem zweiten und dritten Bildschirm
    ModuleWindowsHost(app, listOf(TrackPlanWindows), appIcon)
}
