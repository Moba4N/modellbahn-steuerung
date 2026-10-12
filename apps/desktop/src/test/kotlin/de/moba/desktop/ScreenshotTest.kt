package de.moba.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import de.moba.adapters.catalog.StationCatalog
import de.moba.adapters.simulator.SimulatorStation
import de.moba.core.model.Loco
import de.moba.core.runtime.LicenseInfo
import de.moba.core.station.Direction
import de.moba.core.station.StationConfig
import de.moba.core.station.StationDriver
import de.moba.modules.driving.DrivingModule
import de.moba.modules.driving.api.DrivingService
import de.moba.modules.driving.ui.DrivingPanel
import de.moba.modules.driving.ui.DrivingScreen
import de.moba.modules.driving.ui.DrivingUiState
import de.moba.modules.trackplan.DemoPlans
import de.moba.modules.trackplan.TrackPlanModule
import de.moba.modules.trackplan.TrackPlanRepository
import de.moba.modules.trackplan.api.TrackPlan
import de.moba.modules.trackplan.api.TrackPlanService
import de.moba.modules.trackplan.api.TrackPlanStore
import de.moba.modules.trackplan.api.TrackShape
import de.moba.modules.trackplan.ui.EditTool
import de.moba.modules.trackplan.ui.TrackPlanMode
import de.moba.modules.trackplan.ui.TrackPlanScreen
import de.moba.modules.trackplan.ui.TrackPlanUiState
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.MobaShell
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat

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
            moduleFactory = { listOf(DrivingModule(), TrackPlanModule(TrackPlanRepository(null))) },
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
        sim.setTurnout(30, thrown = false)
        sim.setTurnout(31, thrown = true)
        DrivingUiState.selected.value = 3

        // Zweite App mit Pro-Abo und drei Gleisbildern gleichzeitig
        val proPlans = object : TrackPlanStore {
            override fun load() = listOf(DemoPlans.layout(), DemoPlans.stagingYard(), DemoPlans.industry())
            override fun save(plans: List<TrackPlan>) = Unit
        }
        val pro = MobaApp(
            scope = scope,
            drivers = listOf(namedSimulator),
            moduleFactory = { listOf(DrivingModule(), TrackPlanModule(TrackPlanRepository(proPlans))) },
            license = LicenseInfo.of("pro"),
        )
        pro.connect(StationConfig("simulator")).join()
        val proSim = pro.session.value!!.station as SimulatorStation
        proSim.setTrackPower(true)
        pro.session.value!!.host.service(TrackPlanService::class)!!.apply {
            toggleVisible("schattenbahnhof")
            toggleVisible("industrie")
        }
        proSim.setTurnout(12, thrown = true)
        proSim.setTurnout(22, thrown = true)
        proSim.setTurnout(41, thrown = true)
        proSim.setTurnout(30, thrown = false)
        proSim.setTurnout(31, thrown = true)
        proSim.simulateFeedback(1, 3, occupied = true)
        proSim.simulateFeedback(3, 2, occupied = true)
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
            // Auswahlmenue der Zentralen aufgeklappt (Klick auf das Feld "Digitalzentrale")
            render("pc-zentrale-menue-$suffix", 1440, 900, 1f, click = Offset(330f, 222f)) {
                MobaShell(app, listOf(DrivingScreen), listOf(DrivingPanel))
            }
            val screens = listOf(DrivingScreen, TrackPlanScreen)
            TrackPlanUiState.mode.value = TrackPlanMode.BETRIEB
            render("pc-gleisbild-$suffix", 1440, 900, 1f) {
                MobaShell(app, screens, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
            }
            pro.setDarkTheme(dark)
            render("pc-gleisbild-pro-$suffix", 1440, 900, 1f) {
                MobaShell(pro, screens, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
            }
            TrackPlanUiState.mode.value = TrackPlanMode.BEARBEITEN
            TrackPlanUiState.tool.value = EditTool.Place(TrackShape.TURNOUT_LEFT)
            TrackPlanUiState.selected.value = 6 to 8
            render("pc-gleisbild-editor-$suffix", 1440, 900, 1f) {
                MobaShell(app, screens, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
            }
            render("handy-gleisbild-editor-$suffix", 824, 1780, 2f) {
                MobaShell(app, screens, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
            }
            TrackPlanUiState.mode.value = TrackPlanMode.BETRIEB
            TrackPlanUiState.selected.value = null
            render("handy-gleisbild-pro-$suffix", 824, 1780, 2f) {
                MobaShell(pro, screens, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
            }
        }
        scope.cancel()
        assertTrue(File(outDir, "pc-fahren-dunkel.png").length() > 0)
    }

    private suspend fun render(
        name: String,
        width: Int,
        height: Int,
        density: Float,
        click: Offset? = null,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        val scene = ImageComposeScene(width, height, Density(density), content = content)
        var time = 0L
        repeat(10) { frame ->
            if (frame == 4 && click != null) {
                scene.sendPointerEvent(PointerEventType.Press, click)
                scene.sendPointerEvent(PointerEventType.Release, click)
            }
            scene.render(time)
            time += 16_000_000L
            delay(30)
        }
        val image = scene.render(time)
        File(outDir, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
    }
}
