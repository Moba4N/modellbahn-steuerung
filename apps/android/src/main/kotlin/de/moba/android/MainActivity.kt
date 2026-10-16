package de.moba.android

import android.os.Bundle
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
import de.moba.modules.symbols.ui.SymbolsScreen
import de.moba.modules.vehicles.ui.VehiclesScreen
import de.moba.modules.calibration.ui.CalibrationScreen
import de.moba.modules.routes.ui.RoutesScreen
import de.moba.modules.monitor.ui.MonitorTool
import de.moba.modules.throttles.ui.ThrottleDock
import de.moba.ui.shell.ExitGuard
import de.moba.ui.shell.ImagePicker
import de.moba.ui.shell.MobaShell

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Im Fahrbetrieb bleibt der Bildschirm an.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val app = (application as MobaApplication).app
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
                // Auf dem Tablet quer dieselbe Menueleiste wie unter Windows
                menuBar = true,
            )
        }
    }
}
