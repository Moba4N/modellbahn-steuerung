package de.moba.android

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import de.moba.modules.driving.ui.DrivingPanel
import de.moba.modules.driving.ui.DrivingScreen
import de.moba.modules.trackplan.ui.TrackPlanScreen
import de.moba.modules.vehicles.ui.VehiclesScreen
import de.moba.ui.shell.MobaShell

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Im Fahrbetrieb bleibt der Bildschirm an.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val app = (application as MobaApplication).app
        setContent { MobaShell(app, screens = listOf(DrivingScreen, VehiclesScreen, TrackPlanScreen), panels = listOf(DrivingPanel)) }
    }
}
