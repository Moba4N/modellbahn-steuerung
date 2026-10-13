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
import de.moba.modules.decoder.DecoderModule
import de.moba.modules.decoder.DecoderRepository
import de.moba.modules.decoder.api.DecoderProfile
import de.moba.modules.decoder.api.DecoderService
import de.moba.modules.decoder.api.StandardCvs
import de.moba.modules.decoder.ui.DecoderScreen
import de.moba.modules.decoder.ui.DecoderUiState
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
import de.moba.modules.vehicles.VehicleModule
import de.moba.modules.vehicles.VehicleRepository
import de.moba.modules.vehicles.api.Epoch
import de.moba.modules.vehicles.api.LocoRecord
import de.moba.modules.vehicles.api.Traction
import de.moba.modules.vehicles.api.VehicleGroup
import de.moba.modules.vehicles.api.VehicleService
import de.moba.modules.vehicles.api.WagonCategory
import de.moba.modules.vehicles.api.WagonClass
import de.moba.modules.vehicles.ui.VehicleDraft
import de.moba.modules.vehicles.ui.VehiclesScreen
import de.moba.modules.vehicles.ui.VehiclesUiState
import de.moba.ui.shell.Appearance
import de.moba.ui.shell.DesignStyle
import de.moba.ui.shell.FontChoice
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.MobaShell
import de.moba.ui.shell.ShellUiState
import de.moba.ui.shell.TextSize
import de.moba.ui.shell.WatermarkStrength
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
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface

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
            moduleFactory = { listOf(DrivingModule(), TrackPlanModule(TrackPlanRepository(null)), VehicleModule(VehicleRepository(null)), DecoderModule(DecoderRepository(null))) },
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
            moduleFactory = { listOf(DrivingModule(), TrackPlanModule(TrackPlanRepository(proPlans)), VehicleModule(VehicleRepository(null)), DecoderModule(DecoderRepository(null))) },
            license = LicenseInfo.of("pro", "decoder"),
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

        // Fahrzeuge: kostenlos voll (3 Loks), mit Pro mehr Gueterwagen als die kostenlose Grenze
        val freeVehicles = session.host.service(VehicleService::class)!!
        freeVehicles.saveLoco(
            LocoRecord("fz-lok-1", "BR 218", 3, "BR 218", "DB", Epoch.IV, Traction.DIESEL, manufacturer = "Roco", vmax = 140, lengthMm = 189),
        )
        freeVehicles.saveLoco(LocoRecord("fz-lok-2", "ICE 3", 12, "BR 403", "DB AG", Epoch.V, Traction.RAILCAR))
        freeVehicles.saveLoco(LocoRecord("fz-lok-3", "V 100", 5, "BR 212", "DB", Epoch.III, Traction.DIESEL))
        freeVehicles.saveWagon(WagonClass("fz-pw-1", VehicleGroup.PASSENGER, "Bnrz 451", "Silberling 2. Klasse", WagonCategory.SECOND, "DB", Epoch.IV, 303, 3))
        freeVehicles.saveWagon(WagonClass("fz-pw-2", VehicleGroup.PASSENGER, "BDnrzf 740", "Steuerwagen", WagonCategory.CONTROL, "DB", Epoch.IV, 303, 1))
        val proVehicles = pro.session.value!!.host.service(VehicleService::class)!!
        listOf(
            WagonClass("fz-gw-1", VehicleGroup.FREIGHT, "Eaos", "Offener Hochbordwagen", WagonCategory.OPEN, "DB", Epoch.IV, 162, 8),
            WagonClass("fz-gw-2", VehicleGroup.FREIGHT, "Fals", "Selbstentladewagen", WagonCategory.HOPPER, "DB", Epoch.IV, 140, 6),
            WagonClass("fz-gw-3", VehicleGroup.FREIGHT, "Gbs", "Gedeckter Güterwagen", WagonCategory.COVERED, "DB", Epoch.IV, 162, 4),
            WagonClass("fz-gw-4", VehicleGroup.FREIGHT, "Zacns", "Kesselwagen", WagonCategory.TANK, "VTG", Epoch.V, 140, 5),
            WagonClass("fz-gw-5", VehicleGroup.FREIGHT, "Sgns", "Containertragwagen", WagonCategory.FLAT_SPECIAL, "DB AG", Epoch.V, 227, 2),
        ).forEach { proVehicles.saveWagon(it) }

        // Decoder: Werte aus dem Simulator lesen; mit Zusatzlizenz zwei gespeicherte Programmierungen
        val decoder = session.host.service(DecoderService::class)!!
        val readValues = StandardCvs.readSet.associateWith { decoder.read(it) }
        val proDecoder = pro.session.value!!.host.service(DecoderService::class)!!
        proDecoder.saveProfile(DecoderProfile("decoder-1", "BR 218 Sound", "BR 218", 3, "ESU LokSound 5", readValues + (3 to 20) + (4 to 16)))
        proDecoder.saveProfile(DecoderProfile("decoder-2", "V 100 Werkseinstellung", "V 100", 5, "Lenz Standard+", mapOf(1 to 5, 2 to 4, 3 to 6, 4 to 6, 7 to 15, 8 to 99, 29 to 6)))
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

            val all = listOf(DrivingScreen, VehiclesScreen, TrackPlanScreen)
            VehiclesUiState.group.value = VehicleGroup.LOCOS
            VehiclesUiState.draft.value = VehicleDraft.Loco(freeVehicles.fleet.value.locos.first(), isNew = false)
            render("pc-fahrzeuge-loks-$suffix", 1440, 900, 1f) {
                MobaShell(app, all, listOf(DrivingPanel), initialScreen = VehiclesScreen.moduleId)
            }
            VehiclesUiState.group.value = VehicleGroup.FREIGHT
            VehiclesUiState.draft.value = VehicleDraft.Wagon(proVehicles.fleet.value.wagons.first(), isNew = false)
            render("pc-fahrzeuge-gueterwagen-pro-$suffix", 1440, 900, 1f) {
                MobaShell(pro, all, listOf(DrivingPanel), initialScreen = VehiclesScreen.moduleId)
            }
            VehiclesUiState.group.value = VehicleGroup.PASSENGER
            VehiclesUiState.draft.value = null
            render("handy-fahrzeuge-$suffix", 824, 1780, 2f) {
                MobaShell(app, all, listOf(DrivingPanel), initialScreen = VehiclesScreen.moduleId)
            }
            VehiclesUiState.draft.value = VehicleDraft.Wagon(WagonClass("fz-pw-9", VehicleGroup.PASSENGER, ""), isNew = true)
            render("handy-fahrzeuge-neu-$suffix", 824, 1780, 2f) {
                MobaShell(app, all, listOf(DrivingPanel), initialScreen = VehiclesScreen.moduleId)
            }

            val withDecoder = listOf(DrivingScreen, VehiclesScreen, DecoderScreen, TrackPlanScreen)
            DecoderUiState.values.value = readValues
            DecoderUiState.cv.value = "29"
            DecoderUiState.value.value = "6"
            render("pc-decoder-$suffix", 1440, 900, 1f) {
                MobaShell(app, withDecoder, listOf(DrivingPanel), initialScreen = DecoderScreen.moduleId)
            }
            render("pc-decoder-datenbank-$suffix", 1440, 900, 1f) {
                MobaShell(pro, withDecoder, listOf(DrivingPanel), initialScreen = DecoderScreen.moduleId)
            }
            render("handy-decoder-$suffix", 824, 1780, 2f) {
                MobaShell(app, withDecoder, listOf(DrivingPanel), initialScreen = DecoderScreen.moduleId)
            }
        }

        // Erscheinungsbild: Designs, Schrift, Hintergrundbild als Wasserzeichen, Burger-Menue
        val withDecoder = listOf(DrivingScreen, VehiclesScreen, DecoderScreen, TrackPlanScreen)
        TrackPlanUiState.mode.value = TrackPlanMode.BETRIEB
        app.setDarkTheme(true)
        ShellUiState.menuOpen.value = true
        render("pc-menue-dunkel", 1440, 900, 1f) {
            MobaShell(app, withDecoder, listOf(DrivingPanel), initialScreen = DrivingScreen.moduleId)
        }
        render("handy-menue-dunkel", 824, 1780, 2f) {
            MobaShell(app, withDecoder, listOf(DrivingPanel), initialScreen = DrivingScreen.moduleId)
        }
        ShellUiState.menuOpen.value = false
        assertTrue(app.setBackgroundImage(demoBackground()))
        for ((style, dark) in listOf(DesignStyle.WINDOWS11 to true, DesignStyle.WINDOWS11 to false, DesignStyle.STELLWERK to true, DesignStyle.STELLWERK to false)) {
            app.setDarkTheme(dark)
            app.setAppearance(Appearance(style = style))
            val name = "${style.name.lowercase()}-${if (dark) "dunkel" else "hell"}"
            render("pc-design-$name", 1440, 900, 1f) {
                MobaShell(app, withDecoder, listOf(DrivingPanel), initialScreen = DrivingScreen.moduleId)
            }
            render("pc-design-gleisbild-$name", 1440, 900, 1f) {
                MobaShell(app, withDecoder, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
            }
            render("handy-design-$name", 824, 1780, 2f) {
                MobaShell(app, withDecoder, listOf(DrivingPanel))
            }
        }
        app.setDarkTheme(true)
        app.setAppearance(Appearance(style = DesignStyle.WINDOWS11, font = FontChoice.SERIF, textSize = TextSize.LARGE, watermark = WatermarkStrength.STRONG))
        render("pc-design-schrift-serifen", 1440, 900, 1f) {
            MobaShell(app, withDecoder, listOf(DrivingPanel), initialScreen = VehiclesScreen.moduleId)
        }
        app.setAppearance(Appearance())
        ShellUiState.appearanceOpen.value = true
        render("pc-erscheinungsbild-dunkel", 1440, 900, 1f) {
            MobaShell(app, withDecoder, listOf(DrivingPanel), imagePicker = { it(null) })
        }
        render("handy-erscheinungsbild-dunkel", 824, 1780, 2f) {
            MobaShell(app, withDecoder, listOf(DrivingPanel), imagePicker = { it(null) })
        }
        app.setDarkTheme(false)
        render("pc-erscheinungsbild-hell", 1440, 900, 1f) {
            MobaShell(app, withDecoder, listOf(DrivingPanel), imagePicker = { it(null) })
        }
        ShellUiState.appearanceOpen.value = false

        scope.cancel()
        assertTrue(File(outDir, "pc-fahren-dunkel.png").length() > 0)
    }

    /** Beispielfoto fuer das Wasserzeichen: Abendhimmel mit Gleisen in Fluchtperspektive. */
    private fun demoBackground(): ByteArray {
        val w = 1600
        val h = 1000
        val surface = Surface.makeRasterN32Premul(w, h)
        val canvas = surface.canvas
        canvas.drawRect(
            Rect.makeWH(w.toFloat(), h.toFloat()),
            Paint().apply {
                shader = Shader.makeLinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xFF2B5876.toInt(), 0xFFE0A96D.toInt(), 0xFF3B2F2F.toInt()))
            },
        )
        val rail = Paint().apply {
            color = 0xFF1A1A1A.toInt()
            strokeWidth = 14f
            isAntiAlias = true
        }
        val horizon = h * 0.45f
        val vanish = w / 2f
        // Schwellen
        var y = horizon + 8f
        while (y < h) {
            val t = (y - horizon) / (h - horizon)
            canvas.drawLine(vanish - 40f - t * 700f, y, vanish + 40f + t * 700f, y, rail.apply { strokeWidth = 4f + t * 18f })
            y += 10f + t * 60f
        }
        rail.strokeWidth = 12f
        canvas.drawLine(vanish - 20f, horizon, vanish - 520f, h.toFloat(), rail)
        canvas.drawLine(vanish + 20f, horizon, vanish + 520f, h.toFloat(), rail)
        // Signalmast
        canvas.drawLine(w * 0.78f, h * 0.2f, w * 0.78f, h * 0.75f, rail.apply { strokeWidth = 18f })
        canvas.drawCircle(w * 0.78f, h * 0.22f, 34f, Paint().apply { color = 0xFFE53935.toInt(); isAntiAlias = true })
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
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
