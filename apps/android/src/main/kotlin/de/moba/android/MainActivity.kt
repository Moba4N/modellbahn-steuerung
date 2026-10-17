package de.moba.android

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import de.moba.modules.decoder.ui.DecoderScreen
import de.moba.modules.driving.ui.DrivingPanel
import de.moba.modules.driving.ui.DrivingScreen
import de.moba.modules.trackplan.ui.TrackPlanScreen
import de.moba.modules.trackplan.ui.TrackPlanSounds
import de.moba.modules.trackplan.ui.TrackPlanTrains
import de.moba.modules.symbols.ui.SymbolsScreen
import de.moba.modules.vehicles.ui.VehiclesScreen
import de.moba.modules.calibration.ui.CalibrationScreen
import de.moba.modules.routes.ui.RoutesScreen
import de.moba.modules.monitor.ui.MonitorTool
import de.moba.modules.actions.ui.ActionsMenu
import de.moba.modules.throttles.ui.ThrottleDock
import de.moba.ui.shell.ExitGuard
import de.moba.ui.shell.ImagePicker
import de.moba.ui.shell.MobaShell
import de.moba.ui.shell.SoundSupport
import de.moba.ui.shell.soundFileName
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Im Fahrbetrieb bleibt der Bildschirm an.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val moba = application as MobaApplication
        val app = moba.app
        setContent {
            // Bildauswahl aus Galerie oder Dateien; das Ergebnis geht an den wartenden Aufrufer.
            val pending = remember { arrayOfNulls<(ByteArray?) -> Unit>(1) }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                val bytes = uri?.let { runCatching { contentResolver.openInputStream(it)?.use { s -> s.readBytes() } }.getOrNull() }
                // Nach einer Bildschirmdrehung wartet niemand mehr; dann direkt uebernehmen.
                val callback = pending[0]
                if (callback != null) callback(bytes) else if (bytes != null) app.setBackgroundImage(bytes)
                pending[0] = null
            }
            val picker = remember {
                ImagePicker { onResult ->
                    pending[0] = onResult
                    launcher.launch("image/*")
                }
            }
            // Klangdateien fuer Sound-Zubehoer: Auswahl, Kopie im App-Ordner, Wiedergabe mit MediaPlayer
            val pendingSound = remember { arrayOfNulls<(String?) -> Unit>(1) }
            val soundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                val name = uri?.let { runCatching { storeSound(it) }.getOrNull() }
                pendingSound[0]?.invoke(name)
                pendingSound[0] = null
            }
            val sound = remember {
                object : SoundSupport {
                    override val formats = "MP3, OGG oder WAV."
                    override fun pick(onResult: (String?) -> Unit) {
                        pendingSound[0] = onResult
                        soundLauncher.launch("audio/*")
                    }
                    override fun play(name: String) = playSound(name)
                }
            }
            // Zurueck auf der Startebene fragt nach, wenn noch Gleisbilder ungespeichert sind.
            val exitGuard = remember { ExitGuard() }
            BackHandler { exitGuard.request() }
            MobaShell(
                app,
                screens = listOf(DrivingScreen, VehiclesScreen, DecoderScreen, TrackPlanScreen, SymbolsScreen, CalibrationScreen, RoutesScreen),
                panels = listOf(DrivingPanel),
                docks = listOf(ThrottleDock),
                tools = listOf(MonitorTool),
                imagePicker = picker,
                exitGuard = exitGuard,
                onExit = ::finish,
                sound = sound,
                effects = listOf(TrackPlanSounds, TrackPlanTrains),
                files = moba.files,
                menus = listOf(ActionsMenu),
            )
        }
    }

    /** Klaenge gehoeren zur offenen Anlage. */
    private val soundDir get() = File((application as MobaApplication).layoutFiles.dir, "klaenge")

    private fun storeSound(uri: Uri): String? {
        val display = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
        val name = soundFileName(display ?: "klang")
        soundDir.mkdirs()
        contentResolver.openInputStream(uri)?.use { input -> File(soundDir, name).outputStream().use { input.copyTo(it) } } ?: return null
        return name
    }

    private fun playSound(name: String) {
        val file = File(soundDir, soundFileName(name)).takeIf { it.isFile } ?: return
        runCatching {
            MediaPlayer().apply {
                setDataSource(file.path)
                setOnCompletionListener { it.release() }
                prepare()
                start()
            }
        }
    }
}
