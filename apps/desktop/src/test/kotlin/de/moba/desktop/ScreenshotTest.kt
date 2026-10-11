package de.moba.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import de.moba.adapters.catalog.StationCatalog
import de.moba.adapters.simulator.SimulatorStation
import de.moba.core.model.Loco
import de.moba.core.station.Direction
import de.moba.core.station.StationConfig
import de.moba.core.station.StationDriver
import de.moba.modules.driving.DrivingModule
import de.moba.modules.driving.api.DrivingService
import de.moba.modules.driving.ui.DrivingPanel
import de.moba.modules.driving.ui.DrivingScreen
import de.moba.modules.driving.ui.DrivingUiState
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.MobaShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Rendert die Oberflaeche ohne Bildschirm und speichert PNGs (PC und Handy, hell und dunkel).
 * Laeuft im GitHub-Build; die Bilder landen im Branch "screenshots".
 */
@OptIn(ExperimentalComposeUiApi::class)
class ScreenshotTest {
    private val outDir = File(System.getProperty("screenshots.dir") ?: "build/screenshots")

    private val namedSimulator = object : StationDriver by StationCatalog.simulator {
        override fun create(config: StationConfig) = SimulatorStation(name = "YaMoRC YD7001 (Simulator)")
    }

    @Test
    fun renderScreens() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val app = MobaApp(
            scope = scope,
            drivers = listOf(namedSimulator) + StationCatalog.drivers.filter { it.id != "simulator" },
            moduleFactory = { listOf(DrivingModule()) },
        )
        app.connect(StationConfig("simulator")).join()
        val session = app.session.value!!
        val sim = session.station as SimulatorStation
        app.layout.update {
            it.copy(
                locos = listOf(
                    Loco("br218", "BR 218", 3),
                    Loco("ice3", "ICE 3", 12),
                    Loco("v100", "V 100", 5),
                ),
            )
        }
        sim.setTrackPower(true)
        val driving = session.host.service(DrivingService::class)!!
        driving.drive(3, 64, Direction.FORWARD)
        driving.setFunction(3, 0, on = true)
        driving.drive(12, 96, Direction.FORWARD)
        driving.setFunction(12, 0, on = true)
        driving.drive(5, 0, Direction.REVERSE)
        sim.simulateFeedback(2, 5, occupied = true)
        sim.simulateFeedback(1, 3, occupied = true)
        sim.simulateFeedback(1, 3, occupied = false)
        sim.setTurnout(12, thrown = true)
        DrivingUiState.selected.value = 3
        delay(300)

        outDir.mkdirs()
        for (dark in listOf(true, false)) {
            app.setDarkTheme(dark)
            val suffix = if (dark) "dunkel" else "hell"
            render("pc-uebersicht-$suffix", 1440, 900, 1f) { MobaShell(app, listOf(DrivingScreen), listOf(DrivingPanel)) }
            render("pc-fahren-$suffix", 1440, 900, 1f) {
                MobaShell(app, listOf(DrivingScreen), listOf(DrivingPanel), initialScreen = DrivingScreen.moduleId)
            }
            render("handy-fahren-$suffix", 824, 1780, 2f) {
                MobaShell(app, listOf(DrivingScreen), listOf(DrivingPanel), initialScreen = DrivingScreen.moduleId)
            }
            render("handy-uebersicht-$suffix", 824, 1780, 2f) { MobaShell(app, listOf(DrivingScreen), listOf(DrivingPanel)) }
        }
        scope.cancel()
        assertTrue(File(outDir, "pc-fahren-dunkel.png").length() > 0)
    }

    private suspend fun render(name: String, width: Int, height: Int, density: Float, content: @androidx.compose.runtime.Composable () -> Unit) {
        val scene = ImageComposeScene(width, height, Density(density), content = content)
        var time = 0L
        repeat(10) {
            scene.render(time)
            time += 16_000_000L
            delay(30)
        }
        val image = scene.render(time)
        File(outDir, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
    }
}
