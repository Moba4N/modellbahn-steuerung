package de.moba.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import de.moba.adapters.catalog.StationCatalog
import de.moba.adapters.simulator.SimulatorStation
import de.moba.core.model.Loco
import de.moba.core.model.LocoDynamics
import de.moba.core.runtime.EcLicenseVerifier
import de.moba.core.runtime.License
import de.moba.core.runtime.LicenseInfo
import de.moba.core.runtime.LicenseSigner
import de.moba.core.station.Direction
import de.moba.core.station.StationConfig
import de.moba.core.station.StationDriver
import de.moba.modules.calibration.CalibrationModule
import de.moba.modules.calibration.CalibrationRepository
import de.moba.modules.calibration.api.CalibrationService
import de.moba.modules.calibration.api.CalibrationSetup
import de.moba.modules.calibration.api.CalibrationStore
import de.moba.modules.calibration.api.Contact
import de.moba.modules.calibration.api.MeasuringTrack
import de.moba.modules.calibration.api.Scale
import de.moba.modules.calibration.api.SpeedPoint
import de.moba.modules.calibration.api.SpeedProfile
import de.moba.modules.calibration.api.SpeedStepMode
import de.moba.modules.calibration.ui.CalibrationScreen
import de.moba.modules.calibration.ui.CalibrationUiState
import de.moba.modules.throttles.ThrottleBarModule
import de.moba.modules.throttles.ThrottleBarRepository
import de.moba.modules.throttles.api.ThrottleBarStore
import de.moba.modules.throttles.ui.ThrottleDock
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
import de.moba.modules.trackplan.api.AccessoryProps
import de.moba.modules.trackplan.api.BlockReservation
import de.moba.modules.trackplan.api.TrackPlan
import de.moba.modules.trackplan.api.TrackPlanService
import de.moba.modules.trackplan.api.TrackPlanStore
import de.moba.modules.trackplan.api.TrackShape
import de.moba.modules.trackplan.api.TrackTile
import de.moba.modules.trackplan.ui.EditTool
import de.moba.modules.trackplan.ui.TrackPlanMode
import de.moba.modules.trackplan.ui.TrackPlanScreen
import de.moba.modules.trackplan.ui.TrackPlanUiState
import de.moba.modules.trackplan.ui.TrackPlanWindows
import de.moba.modules.monitor.MonitorModule
import de.moba.modules.monitor.api.MONITOR_MODULE_ID
import de.moba.modules.monitor.api.MonitorKind
import de.moba.modules.monitor.ui.MonitorTool
import de.moba.modules.monitor.ui.MonitorUiState
import de.moba.modules.actions.ActionModule
import de.moba.modules.actions.ActionRepository
import de.moba.modules.actions.api.ActionBook
import de.moba.modules.actions.api.ActionRule
import de.moba.modules.actions.api.ActionStore
import de.moba.modules.actions.api.Command
import de.moba.modules.actions.api.CommandType
import de.moba.modules.actions.api.Condition
import de.moba.modules.actions.api.ConditionType
import de.moba.modules.actions.ui.ActionsMenu
import de.moba.modules.actions.ui.ActionsTab
import de.moba.modules.actions.ui.ActionsUiState
import de.moba.modules.actions.ui.ActionsWindow
import de.moba.modules.routes.RouteModule
import de.moba.modules.routes.RouteRepository
import de.moba.modules.routes.api.Feedback
import de.moba.modules.routes.api.Route
import de.moba.modules.routes.api.RouteBook
import de.moba.modules.routes.api.RouteService
import de.moba.modules.routes.api.RouteSetting
import de.moba.modules.routes.api.RouteStore
import de.moba.modules.routes.api.Shuttle
import de.moba.modules.routes.api.TrainRoute
import de.moba.modules.routes.api.TrainRouteStep
import de.moba.modules.routes.ui.RoutePick
import de.moba.modules.routes.ui.RoutesScreen
import de.moba.modules.routes.ui.RoutesTab
import de.moba.modules.routes.ui.RoutesUiState
import de.moba.modules.trackplan.api.Cell
import de.moba.modules.symbols.SymbolModule
import de.moba.modules.symbols.SymbolRepository
import de.moba.modules.symbols.api.SignalCatalog
import de.moba.modules.symbols.api.SignalSymbols
import de.moba.modules.symbols.api.SymbolService
import de.moba.modules.symbols.ui.SymbolsScreen
import de.moba.modules.symbols.ui.SymbolsTab
import de.moba.modules.symbols.ui.SymbolsUiState
import de.moba.modules.vehicles.VehicleModule
import de.moba.modules.vehicles.VehicleRepository
import de.moba.modules.vehicles.api.Epoch
import de.moba.modules.vehicles.api.Gauge
import de.moba.modules.vehicles.api.LocoRecord
import de.moba.modules.vehicles.api.Traction
import de.moba.modules.vehicles.api.VehicleGroup
import de.moba.modules.vehicles.api.VehicleService
import de.moba.modules.vehicles.api.WagonCategory
import de.moba.modules.vehicles.api.WagonClass
import de.moba.modules.vehicles.ui.VehicleDraft
import de.moba.modules.vehicles.ui.VehiclesScreen
import de.moba.modules.vehicles.ui.VehiclesUiState
import de.moba.ui.shell.AccentColor
import de.moba.ui.shell.Appearance
import de.moba.ui.shell.FunctionKeysState
import de.moba.ui.shell.FunctionRange
import de.moba.ui.shell.DesignStyle
import de.moba.ui.shell.FontChoice
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.MobaShell
import de.moba.ui.shell.FileDialogKind
import de.moba.ui.shell.FileSupport
import de.moba.ui.shell.FileUiState
import de.moba.core.files.FolderLayoutFiles
import de.moba.ui.shell.ModuleWindowFrame
import de.moba.ui.shell.ShellUiState
import de.moba.ui.shell.SoundSupport
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

    private val testKeys = LicenseSigner.generateKeyPair()
    private val testSigner = LicenseSigner(testKeys.first)

    @Test
    fun renderScreens() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val app = MobaApp(
            scope = scope,
            drivers = listOf(namedSimulator) + StationCatalog.drivers.filter { it.id != "simulator" },
            moduleFactory = { listOf(DrivingModule(), TrackPlanModule(TrackPlanRepository(null)), VehicleModule(VehicleRepository(null)), DecoderModule(DecoderRepository(null)), SymbolModule(SymbolRepository(null))) },
            licenseVerifier = EcLicenseVerifier(testKeys.second),
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
        val proThrottles = object : ThrottleBarStore {
            var saved = listOf(3, 12, 5)
            override fun load() = saved
            override fun save(addresses: List<Int>) {
                saved = addresses
            }
        }
        val proCalibration = object : CalibrationStore {
            var saved = listOf(
                SpeedProfile(
                    3, "BR 218", SpeedStepMode.STEPS_28, Scale.H0,
                    (1..28).map { SpeedPoint(it, if (it < 2) null else 4.0 + it * 5.2 - it * it * 0.04) }, "2026-10-14",
                ),
            )
            override fun load() = saved
            override fun save(profiles: List<SpeedProfile>) {
                saved = profiles
            }
        }
        val proRoutes = RouteRepository(
            object : RouteStore {
                override fun load() = RouteBook(
                    routes = listOf(
                        Route("fs-1", "Bahnhof Gleis 1 → Strecke", listOf(RouteSetting(12, true), RouteSetting(22, false), RouteSetting(30, false)), Feedback(1, 1), Feedback(1, 4)),
                        Route("fs-2", "Strecke → Bahnhof Gleis 2", listOf(RouteSetting(41, true), RouteSetting(30, true)), Feedback(1, 5), Feedback(1, 8)),
                        Route("fs-3", "Schattenbahnhof Ausfahrt", listOf(RouteSetting(51, false), RouteSetting(52, true)), null, Feedback(2, 3)),
                    ),
                    shuttles = listOf(
                        Shuttle("pe-1", "Nebenbahn Pendel", 12, speed = 50, endA = Feedback(3, 1), endB = Feedback(3, 8), brakeB = Feedback(3, 7), waitSeconds = 20),
                    ),
                    trainRoutes = listOf(
                        TrainRoute(
                            "zr-1", "Güterzug Rundfahrt", 5, speed = 70,
                            steps = listOf(TrainRouteStep("fs-1", waitSeconds = 15), TrainRouteStep("fs-2", stopAt = Feedback(1, 7), speed = 40)),
                            repeat = true,
                        ),
                    ),
                )
                override fun save(book: RouteBook) = Unit
            },
        )
        // Aktionen wie in iTrain: Bedingungsbaum aus Block, Zeit, Zufall, Brennstoff, Gattung, Spurweite und Wagen
        val proActions = ActionRepository(
            object : ActionStore {
                override fun load() = ActionBook(
                    rules = listOf(
                        ActionRule(
                            "ak-1", "Einfahrt Bahnhof: Signal auf Fahrt",
                            condition = Condition.and(
                                Condition.of(ConditionType.BLOCK_OCCUPIED, "block" to "1.3"),
                                Condition.of(ConditionType.BLOCK_FREE, "block" to "1.4"),
                                Condition.of(ConditionType.TIME, "von" to "05:00", "bis" to "23:30"),
                            ),
                            commands = listOf(
                                Command.of(CommandType.ACCESSORY, "adresse" to "30", "stellung" to "gerade"),
                                Command.of(CommandType.LOCO_FUNCTION, "lok" to "5", "funktion" to "2", "zustand" to "an"),
                            ),
                        ),
                        ActionRule(
                            "ak-2", "Dampflok fasst Wasser",
                            condition = Condition.and(
                                Condition.of(ConditionType.FUEL, "lok" to "50", "vergleich" to "unter", "prozent" to "40"),
                                Condition.of(ConditionType.BLOCK_POSITION, "block" to "3.2", "position" to "halt"),
                                Condition.of(ConditionType.CATEGORY, "block" to "3.2", "gattung" to "STEAM"),
                            ),
                            commands = listOf(
                                Command.of(CommandType.LOCO_FUNCTION, "lok" to "50", "funktion" to "4", "zustand" to "an"),
                                Command.of(CommandType.WAIT, "sekunden" to "20"),
                                Command.of(CommandType.REFUEL, "lok" to "50"),
                                Command.of(CommandType.MESSAGE, "text" to "BR 50 hat Wasser gefasst"),
                            ),
                        ),
                        ActionRule(
                            "ak-3", "Nachts manchmal ein Kesselzug",
                            condition = Condition.and(
                                Condition.of(ConditionType.TRAIN_ROUTE, "zugroute" to "zr-1", "zustand" to "steht"),
                                Condition.of(ConditionType.RANDOM, "prozent" to "30"),
                                Condition.or(
                                    Condition.of(ConditionType.WAGON, "block" to "1.3", "bauart" to "TANK"),
                                    Condition(ConditionType.GAUGE, mapOf("block" to "1.3", "spur" to "H0M"), negate = true),
                                ),
                            ),
                            commands = listOf(Command.of(CommandType.AUTOMATION_START, "zugroute" to "zr-1")),
                        ),
                        ActionRule(
                            "ak-4", "Betriebsschluss", enabled = false,
                            condition = Condition.of(ConditionType.TIME, "von" to "23:30", "bis" to "05:00", "uhr" to "echt"),
                            commands = listOf(Command.of(CommandType.EMERGENCY_STOP), Command.of(CommandType.TRACK_POWER, "zustand" to "aus")),
                        ),
                    ),
                    fuel = mapOf(50 to 34.0, 5 to 81.0),
                )
                override fun save(book: ActionBook) = Unit
            },
        )
        val pro = MobaApp(
            scope = scope,
            drivers = listOf(namedSimulator),
            moduleFactory = {
                listOf(
                    DrivingModule(), TrackPlanModule(TrackPlanRepository(proPlans)), VehicleModule(VehicleRepository(null)),
                    DecoderModule(DecoderRepository(null)), SymbolModule(SymbolRepository(null)),
                    ThrottleBarModule(ThrottleBarRepository(proThrottles)), CalibrationModule(CalibrationRepository(proCalibration)),
                    RouteModule(proRoutes), MonitorModule(), ActionModule(proActions),
                )
            },
            license = LicenseInfo.of("pro", "decoder"),
        )
        pro.connect(StationConfig("simulator")).join()
        val proSim = pro.session.value!!.station as SimulatorStation
        pro.layout.update { it.copy(locos = app.layout.layout.value.locos) }
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
        freeVehicles.setImage("fz-lok-1", demoLocoPhoto())
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

        // Symbole: Pixel-Editor wie TrainController, Licht- und Formsignale, Einsatz im Gleisbild
        app.setDarkTheme(true)
        pro.setDarkTheme(true)
        val withSymbols = listOf(DrivingScreen, VehiclesScreen, DecoderScreen, TrackPlanScreen, SymbolsScreen)
        val proSymbols = pro.session.value!!.host.service(SymbolService::class)!!
        val ks = SignalSymbols.toSymbol(SignalCatalog.system("de-ks")!!, proSymbols.newId())
        proSymbols.update(ks)
        SymbolsUiState.tab.value = SymbolsTab.OWN
        SymbolsUiState.select("sym-1")
        SymbolsUiState.stateIndex.value = 1
        render("pc-symbole-editor-dunkel", 1440, 900, 1f) {
            MobaShell(app, withSymbols, listOf(DrivingPanel), initialScreen = SymbolsScreen.moduleId)
        }
        render("handy-symbole-editor-dunkel", 824, 1780, 2f) {
            MobaShell(app, withSymbols, listOf(DrivingPanel), initialScreen = SymbolsScreen.moduleId)
        }
        SymbolsUiState.select(ks.id)
        SymbolsUiState.stateIndex.value = 1
        render("pc-symbole-signal-pro-dunkel", 1440, 900, 1f) {
            MobaShell(pro, withSymbols, listOf(DrivingPanel), initialScreen = SymbolsScreen.moduleId)
        }
        SymbolsUiState.tab.value = SymbolsTab.SIGNALS
        for ((country, system) in listOf("DE" to "de-hv-hp", "DE" to "de-ks", "DE" to "de-form-hp2", "DE" to "de-form-vr", "CH" to "ch-l-hs", "CH" to "ch-form-hs", "NL" to "nl-main", "NL" to "nl-arm")) {
            SymbolsUiState.country.value = country
            SymbolsUiState.system.value = system
            render("pc-signalbilder-$system-dunkel", 1440, 900, 1f) {
                MobaShell(app, withSymbols, listOf(DrivingPanel), initialScreen = SymbolsScreen.moduleId)
            }
        }
        SymbolsUiState.country.value = "NL"
        SymbolsUiState.system.value = null
        render("handy-signalbilder-dunkel", 824, 1780, 2f) {
            MobaShell(app, withSymbols, listOf(DrivingPanel), initialScreen = SymbolsScreen.moduleId)
        }
        app.setDarkTheme(false)
        SymbolsUiState.tab.value = SymbolsTab.OWN
        SymbolsUiState.select("sym-2")
        render("pc-symbole-editor-hell", 1440, 900, 1f) {
            MobaShell(app, withSymbols, listOf(DrivingPanel), initialScreen = SymbolsScreen.moduleId)
        }

        // Gleisbild mit Ks-Signalen und einer Bahnsteiglampe
        val proPlansService = pro.session.value!!.host.service(TrackPlanService::class)!!
        val main = proPlansService.plans.value.first()
        var withSignals = main
        main.tiles.filter { it.shape == TrackShape.SIGNAL }.forEachIndexed { i, tile ->
            withSignals = withSignals.put(tile.copy(symbol = if (i == 0) "signal:de-ks" else "signal:de-form-hp2"))
        }
        val free = (0 until main.width).first { x -> withSignals.tileAt(x, 0) == null }
        withSignals = withSignals.put(TrackTile(free, 0, TrackShape.SYMBOL, symbol = "sym-1"))
        proPlansService.update(withSignals)
        TrackPlanUiState.mode.value = TrackPlanMode.BETRIEB
        render("pc-gleisbild-signale-dunkel", 1440, 900, 1f) {
            MobaShell(pro, withSymbols, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
        }
        TrackPlanUiState.mode.value = TrackPlanMode.BEARBEITEN
        TrackPlanUiState.editPlan.value = main.id
        TrackPlanUiState.tool.value = EditTool.Select
        TrackPlanUiState.selected.value = main.tiles.first { it.shape == TrackShape.SIGNAL }.let { it.x to it.y }
        render("pc-gleisbild-signal-auswahl-dunkel", 1440, 900, 1f) {
            MobaShell(pro, withSymbols, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
        }
        TrackPlanUiState.selected.value = null
        TrackPlanUiState.mode.value = TrackPlanMode.BETRIEB

        // Mehrere Fahrregler am unteren Rand und Einmessen (beides ab Pro)
        val proDriving = pro.session.value!!.host.service(DrivingService::class)!!
        proDriving.drive(3, 64, Direction.FORWARD)
        proDriving.setFunction(3, 0, on = true)
        proDriving.drive(12, 30, Direction.REVERSE)
        delay(200)
        val withCalibration = withSymbols + CalibrationScreen
        app.setDarkTheme(true)
        render("pc-fahrregler-pro-dunkel", 1440, 900, 1f) {
            MobaShell(pro, withCalibration, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId, docks = listOf(ThrottleDock))
        }
        app.setDarkTheme(false)
        render("handy-fahrregler-pro-hell", 824, 1780, 2f) {
            MobaShell(pro, withCalibration, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId, docks = listOf(ThrottleDock))
        }
        render("pc-einmessen-pro-hell", 1440, 900, 1f) {
            MobaShell(pro, withCalibration, listOf(DrivingPanel), initialScreen = CalibrationScreen.moduleId)
        }
        CalibrationUiState.address.value = "5"
        pro.session.value!!.host.service(CalibrationService::class)!!.start(
            CalibrationSetup(5, "V 100", SpeedStepMode.STEPS_128, MeasuringTrack(Contact(1, 1), Contact(1, 2), 1000)),
        )
        delay(200)
        app.setDarkTheme(true)
        render("pc-einmessen-laeuft-pro-dunkel", 1440, 900, 1f) {
            MobaShell(pro, withCalibration, listOf(DrivingPanel), initialScreen = CalibrationScreen.moduleId)
        }
        render("handy-einmessen-pro-dunkel", 824, 1780, 2f) {
            MobaShell(pro, withCalibration, listOf(DrivingPanel), initialScreen = CalibrationScreen.moduleId)
        }
        pro.session.value!!.host.service(CalibrationService::class)!!.cancel()

        // Fahrstrassen, Pendelstrecken und Zugrouten (stellen ab Basis, Automatik ab Pro)
        val withRoutes = withCalibration + RoutesScreen
        val proRouteService = pro.session.value!!.host.service(RouteService::class)!!
        proRouteService.setRoute("fs-1")
        RoutesUiState.tab.value = RoutesTab.ROUTES
        RoutesUiState.select("fs-1")
        render("pc-fahrstrassen-pro-dunkel", 1440, 900, 1f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = RoutesScreen.moduleId)
        }
        RoutesUiState.select(null)
        app.setDarkTheme(false)
        render("handy-fahrstrassen-pro-hell", 824, 1780, 2f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = RoutesScreen.moduleId)
        }
        RoutesUiState.select("fs-2")
        render("handy-fahrstrasse-bearbeiten-pro-hell", 824, 1780, 2f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = RoutesScreen.moduleId)
        }
        proRouteService.releaseRoute("fs-1")
        proRouteService.startShuttle("pe-1")
        delay(200)
        RoutesUiState.tab.value = RoutesTab.SHUTTLES
        RoutesUiState.select("pe-1")
        render("pc-pendelstrecke-pro-hell", 1440, 900, 1f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = RoutesScreen.moduleId)
        }
        proRouteService.stopAutomation("pe-1")
        proRouteService.startTrainRoute("zr-1")
        delay(500)
        app.setDarkTheme(true)
        RoutesUiState.tab.value = RoutesTab.TRAINS
        RoutesUiState.select("zr-1")
        render("pc-zugroute-pro-dunkel", 1440, 900, 1f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = RoutesScreen.moduleId)
        }
        RoutesUiState.select(null)
        render("handy-zugrouten-pro-dunkel", 824, 1780, 2f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = RoutesScreen.moduleId)
        }
        proRouteService.stopAutomation("zr-1")
        delay(100)

        // Fahrstrasse im Gleisbild waehlen: Start und Ziel angetippt, Wege gefunden
        RoutesUiState.tab.value = RoutesTab.ROUTES
        RoutesUiState.select(RoutesUiState.NEW)
        val pickId = proRouteService.newId("fs-")
        RoutesUiState.routePick.value = RoutePick(pickId, open = true, planId = "beispiel", from = Cell(9, 2), to = Cell(10, 6))
        render("pc-fahrstrasse-aus-gleisbild-dunkel", 1440, 900, 1f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = RoutesScreen.moduleId)
        }
        app.setDarkTheme(false)
        RoutesUiState.routePick.value = RoutesUiState.routePick.value.copy(choice = 1)
        render("handy-fahrstrasse-aus-gleisbild-hell", 824, 1780, 2f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = RoutesScreen.moduleId)
        }
        app.setDarkTheme(true)
        RoutesUiState.routePick.value = RoutePick()
        RoutesUiState.select(null)

        // Fahrpult rechts eingeblendet (anfangs aus) und groessere Symbole in der Leiste "Gleisformen"
        app.setDarkTheme(true)
        app.setPreference("shell.fahrpult", "an")
        render("pc-fahrpult-eingeblendet-dunkel", 1440, 900, 1f) {
            MobaShell(app, withDecoder, listOf(DrivingPanel), initialScreen = DrivingScreen.moduleId)
        }
        app.setPreference("shell.fahrpult", "aus")
        render("pc-fahren-ohne-fahrpult-dunkel", 1440, 900, 1f) {
            MobaShell(app, withDecoder, listOf(DrivingPanel), initialScreen = DrivingScreen.moduleId)
        }
        app.setPreference("gleisbild.symbolgroesse", "48")
        TrackPlanUiState.mode.value = TrackPlanMode.BEARBEITEN
        TrackPlanUiState.editPlan.value = null
        render("pc-gleisformen-gross-dunkel", 1440, 900, 1f) {
            MobaShell(app, withDecoder, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
        }
        app.setPreference("gleisbild.symbolgroesse", "30")
        TrackPlanUiState.mode.value = TrackPlanMode.BETRIEB

        // Monitor (nur im Burger-Menue unter "Werkzeuge"), Funktionen bis F31, Akzentfarben und Schriften
        val tools = listOf(MonitorTool)
        pro.setDarkTheme(true)
        ShellUiState.menuOpen.value = true
        render("pc-menue-werkzeuge-dunkel", 1440, 900, 1f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = DrivingScreen.moduleId, tools = tools)
        }
        ShellUiState.menuOpen.value = false
        ShellUiState.openTool.value = MONITOR_MODULE_ID
        render("pc-monitor-dunkel", 1440, 900, 1f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = DrivingScreen.moduleId, tools = tools)
        }
        MonitorUiState.kinds.value = MonitorKind.entries.toSet()
        MonitorUiState.showHex.value = false
        pro.setDarkTheme(false)
        render("handy-monitor-hell", 824, 1780, 2f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = DrivingScreen.moduleId, tools = tools)
        }
        ShellUiState.openTool.value = null
        MonitorUiState.kinds.value = setOf(MonitorKind.SENT, MonitorKind.RECEIVED)
        MonitorUiState.showHex.value = true

        listOf(4, 17, 30).forEach { proDriving.setFunction(3, it, on = true) }
        delay(100)
        FunctionKeysState.set(pro, FunctionRange.F31)
        DrivingUiState.selected.value = 3
        pro.setPreference("shell.fahrpult", "an")
        pro.setDarkTheme(true)
        render("pc-fahrpult-32-funktionen-dunkel", 1440, 900, 1f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = DrivingScreen.moduleId)
        }
        pro.setDarkTheme(false)
        render("handy-fahrpult-32-funktionen-hell", 824, 1780, 2f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = DrivingScreen.moduleId)
        }
        pro.setDarkTheme(true)
        pro.setAppearance(Appearance(accent = AccentColor.ORANGE, font = FontChoice.OUTFIT))
        render("pc-akzent-orange-outfit-dunkel", 1440, 900, 1f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = DrivingScreen.moduleId)
        }
        pro.setAppearance(Appearance(style = DesignStyle.STELLWERK, accent = AccentColor.TEAL, font = FontChoice.TEKTUR))
        render("pc-akzent-tuerkis-tektur-dunkel", 1440, 900, 1f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
        }
        pro.setAppearance(Appearance(accent = AccentColor.PURPLE, font = FontChoice.LORA))
        ShellUiState.appearanceOpen.value = true
        render("pc-erscheinungsbild-akzent-dunkel", 1440, 900, 1f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = DrivingScreen.moduleId)
        }
        pro.setDarkTheme(false)
        render("handy-erscheinungsbild-akzent-hell", 824, 1780, 2f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = DrivingScreen.moduleId)
        }
        ShellUiState.appearanceOpen.value = false
        pro.setAppearance(Appearance())
        FunctionKeysState.set(pro, FunctionRange.F12)
        FunctionKeysState.reset()
        DrivingUiState.selected.value = null

        // Fahreigenschaften der Lok und Fuehrerstand mit Traegheit (die Lok faehrt noch hoch, die Marke zeigt den Fahrhebel)
        app.setDarkTheme(true)
        val heavy = LocoDynamics(cab = true, inertia = true, accelerationSeconds = 120.0, brakingSeconds = 60.0, periodMs = 250, feedbackOffsetMm = 60, reactionDelayMs = 120)
        VehiclesUiState.group.value = VehicleGroup.LOCOS
        VehiclesUiState.draft.value = VehicleDraft.Loco(freeVehicles.fleet.value.locos.first().copy(dynamics = heavy), isNew = false)
        render("pc-fahrzeuge-lok-fahreigenschaften-dunkel", 1440, 1700, 1f) {
            MobaShell(app, listOf(DrivingScreen, VehiclesScreen), listOf(DrivingPanel), initialScreen = VehiclesScreen.moduleId)
        }
        app.setDarkTheme(false)
        render("handy-fahrzeuge-lok-fahreigenschaften-hell", 824, 3400, 2f) {
            MobaShell(app, listOf(DrivingScreen, VehiclesScreen), listOf(DrivingPanel), initialScreen = VehiclesScreen.moduleId)
        }
        VehiclesUiState.draft.value = null
        pro.layout.update { l -> l.copy(locos = l.locos.map { if (it.address == 12) it.copy(dynamics = heavy, vmaxKmh = 300) else it }) }
        proSim.setLocoSpeed(12, 48, Direction.FORWARD)
        delay(100)
        proDriving.drive(12, 110, Direction.FORWARD)
        DrivingUiState.selected.value = 12
        pro.setPreference("shell.fahrpult", "an")
        pro.setDarkTheme(true)
        render("pc-fuehrerstand-traegheit-dunkel", 1440, 900, 1f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = DrivingScreen.moduleId)
        }
        pro.setDarkTheme(false)
        render("handy-fuehrerstand-traegheit-hell", 824, 1780, 2f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = DrivingScreen.moduleId)
        }
        proDriving.emergencyBrake(12)
        pro.layout.update { l -> l.copy(locos = l.locos.map { if (it.address == 12) it.copy(dynamics = LocoDynamics(), vmaxKmh = null) else it }) }
        DrivingUiState.selected.value = null

        // Gleisbilder in eigenen Fenstern (Pro)
        pro.setDarkTheme(true)
        TrackPlanUiState.mode.value = TrackPlanMode.BETRIEB
        ShellUiState.separateWindows.value = true
        TrackPlanWindows.reset()
        TrackPlanWindows.detach(pro, "schattenbahnhof")
        TrackPlanWindows.detach(pro, "industrie")
        render("pc-gleisbild-hauptfenster-zwei-fenster-dunkel", 1440, 900, 1f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId, separateWindows = true)
        }
        for ((id, name) in listOf("schattenbahnhof" to "Schattenbahnhof", "industrie" to "Industrie")) {
            render("pc-gleisbild-fenster-$id-dunkel", 1920, 1080, 1f) {
                ModuleWindowFrame(pro, name, fullscreen = false, onToggleFullscreen = {}, onClose = {}) { TrackPlanWindows.Content(pro, id) }
            }
        }
        pro.setDarkTheme(false)
        render("pc-gleisbild-fenster-industrie-hell", 1920, 1080, 1f) {
            ModuleWindowFrame(pro, "Industrie", fullscreen = true, onToggleFullscreen = {}, onClose = {}) { TrackPlanWindows.Content(pro, "industrie") }
        }
        TrackPlanWindows.reset()
        ShellUiState.separateWindows.value = false

        // Bloecke wie in iTrain: Zuege in Bloecken, eine reservierte Fahrstrasse, Kontextmenue
        pro.setDarkTheme(true)
        TrackPlanUiState.mode.value = TrackPlanMode.BETRIEB
        proPlansService.placeTrain(1 to 3, 3)
        proPlansService.placeTrain(2 to 5, 12)
        proPlansService.reserve(listOf(1 to 4), BlockReservation("fs:demo", "Fahrstraße „Einfahrt Gleis 1“"))
        delay(100)
        render("pc-gleisbild-bloecke-zuege-dunkel", 1440, 900, 1f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
        }
        TrackPlanUiState.contextMenu.value = "beispiel" to (12 to 8)
        render("pc-gleisbild-kontextmenue-block-dunkel", 1440, 900, 1f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
        }
        TrackPlanUiState.contextMenu.value = "beispiel" to (6 to 8)
        render("pc-gleisbild-kontextmenue-weiche-dunkel", 1440, 900, 1f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
        }
        pro.setDarkTheme(false)
        TrackPlanUiState.contextMenu.value = "beispiel" to (9 to 2)
        render("handy-gleisbild-kontextmenue-block-hell", 824, 1780, 2f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
        }
        TrackPlanUiState.contextMenu.value = null
        render("handy-gleisbild-bloecke-zuege-hell", 824, 1780, 2f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
        }
        proPlansService.releaseAll()
        proPlansService.trains.value.keys.forEach(proPlansService::removeTrain)

        // Zubehoer wie in iTrain: Drehscheibe, Schiebebuehne, Bahnuebergang, Relais, Licht, Sound, Zubehoer mit Bildern
        pro.setDarkTheme(true)
        val accessoryPlan = proPlansService.addPlan("Zubehör").resize(28, 16).let { start ->
            var p = start
            fun put(t: TrackTile) { p = p.put(t) }
            (0..15).forEach { x -> put(TrackTile(x, 2, if (x == 5) TrackShape.LEVEL_CROSSING else TrackShape.STRAIGHT, 2, address = if (x == 5) 30 else null)) }
            p = p.put(p.tileAt(5, 2)!!.withProp(AccessoryProps.NAME, "BÜ Dorfstraße").withProp(AccessoryProps.ON_FEEDBACK, "1.3, 1.4").withProp(AccessoryProps.OFF_FEEDBACK, "1.8"))
            put(TrackTile(4, 1, TrackShape.TEXT, text = "BÜ Dorfstraße"))
            (3..6).forEach { y -> put(TrackTile(8, y, TrackShape.STRAIGHT, 0)) }
            listOf(12 to 6, 13 to 5).forEach { (x, y) -> put(TrackTile(x, y, TrackShape.STRAIGHT, 1)) }
            listOf(4 to 6, 3 to 5).forEach { (x, y) -> put(TrackTile(x, y, TrackShape.STRAIGHT, 7)) }
            (12..15).forEach { x -> put(TrackTile(x, 10, TrackShape.STRAIGHT, 2)) }
            put(TrackTile(16, 10, TrackShape.BUFFER_STOP, 6))
            (1..4).forEach { x -> put(TrackTile(x, 10, TrackShape.STRAIGHT, 2)) }
            put(TrackTile(0, 10, TrackShape.BUFFER_STOP, 2))
            put(TrackTile(8, 14, TrackShape.STRAIGHT, 0))
            put(TrackTile(8, 15, TrackShape.BUFFER_STOP, 0))
            put(
                TrackTile(8, 10, TrackShape.TURNTABLE, address = 225)
                    .withProp(AccessoryProps.NAME, "Drehscheibe Bw")
                    .withProp(AccessoryProps.POSITIONS, "24")
                    .withProp(AccessoryProps.TRACKS, "0, 3, 6, 12, 18, 21")
                    .withProp(AccessoryProps.SIZE, "3")
                    .withProp(AccessoryProps.CMD_TURN, "241:r")
                    .withProp(AccessoryProps.CMD_STEP_CW, "242:g")
                    .withProp(AccessoryProps.CMD_STEP_CCW, "242:r")
                    .withProp(AccessoryProps.READY_FEEDBACK, "4.1")
                    .withProp(AccessoryProps.BLOCK, "Bühne")
                    .withProp(AccessoryProps.BLOCK_FEEDBACK, "4.2")
                    .withProp(AccessoryProps.BLOCK_LOCK, "1"),
            )
            put(TrackTile(20, 4, TrackShape.TRANSFER_TABLE, address = 250).withProp(AccessoryProps.NAME, "Schiebebühne Lokschuppen")
                .withProp(AccessoryProps.POSITIONS, "5").withProp(AccessoryProps.SIZE, "3").withProp(AccessoryProps.STEP_TIME, "1500"))
            (4..8).forEach { y ->
                (23..25).forEach { x -> put(TrackTile(x, y, TrackShape.STRAIGHT, 2)) }
                put(TrackTile(26, y, TrackShape.BUFFER_STOP, 6))
            }
            listOf(4, 6, 8).forEach { y -> (17..19).forEach { x -> put(TrackTile(x, y, TrackShape.STRAIGHT, 2)) } }
            put(TrackTile(18, 12, TrackShape.RELAY, address = 70).withProp(AccessoryProps.NAME, "Fahrstrom Bw"))
            put(TrackTile(20, 12, TrackShape.LIGHT, address = 71).withProp(AccessoryProps.NAME, "Lampen Bw"))
            put(TrackTile(22, 12, TrackShape.SOUND, address = 72).withProp(AccessoryProps.FILE, "pfiff.wav").withProp(AccessoryProps.ON_FEEDBACK, "2.1"))
            put(TrackTile(24, 12, TrackShape.ACCESSORY_ASPECT, address = 80).withProp(AccessoryProps.ASPECTS, "Aus|Blinken|Dauerlicht|Nacht"))
            listOf(18 to "Relais", 20 to "Licht", 22 to "Sound", 24 to "Bilder").forEach { (x, t) -> put(TrackTile(x - 1, 13, TrackShape.TEXT, text = t)) }
            p
        }
        proPlansService.update(accessoryPlan)
        val previousVisible = proPlansService.visiblePlans.value
        proPlansService.visiblePlans.value.forEach { if (proPlansService.visiblePlans.value.size > 1) proPlansService.toggleVisible(it) }
        proPlansService.toggleVisible(accessoryPlan.id)
        proPlansService.visiblePlans.value.filter { it != accessoryPlan.id }.forEach { proPlansService.toggleVisible(it) }
        val turntable = accessoryPlan.tileAt(8, 10)!!
        proPlansService.turnTo(turntable, 6)
        proSim.simulateFeedback(4, 1, occupied = true)
        proPlansService.turnTo(accessoryPlan.tileAt(20, 4)!!, 2)
        proPlansService.setOn(accessoryPlan.tileAt(5, 2)!!, true)
        proPlansService.setOn(accessoryPlan.tileAt(20, 12)!!, true)
        proPlansService.setAspect(accessoryPlan.tileAt(24, 12)!!, 1)
        delay(200)
        TrackPlanUiState.mode.value = TrackPlanMode.BETRIEB
        TrackPlanUiState.tileSize.value = 30
        render("pc-gleisbild-zubehoer-betrieb-dunkel", 1440, 900, 1f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
        }
        TrackPlanUiState.openTable.value = accessoryPlan.id to (8 to 10)
        render("pc-gleisbild-drehscheibe-steuern-dunkel", 1440, 900, 1f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
        }
        pro.setDarkTheme(false)
        render("handy-gleisbild-drehscheibe-steuern-hell", 824, 1780, 2f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
        }
        TrackPlanUiState.openTable.value = null
        render("handy-gleisbild-zubehoer-betrieb-hell", 824, 1780, 2f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
        }
        pro.setDarkTheme(true)
        val demoSound = object : SoundSupport {
            override val formats = "WAV, AIFF oder AU."
            override fun pick(onResult: (String?) -> Unit) = onResult(null)
            override fun play(name: String) = Unit
        }
        TrackPlanUiState.mode.value = TrackPlanMode.BEARBEITEN
        TrackPlanUiState.editPlan.value = accessoryPlan.id
        TrackPlanUiState.tool.value = EditTool.Select
        for ((cell, tabs) in listOf((8 to 10) to listOf("allgemein", "decoder", "zubehoer", "rueckmelder", "block", "steuern"), (5 to 2) to listOf("allgemein", "decoder", "steuerung"), (22 to 12) to listOf("datei"))) {
            TrackPlanUiState.selected.value = cell
            tabs.forEachIndexed { i, tab ->
                TrackPlanUiState.accessoryTab.value = i
                val what = accessoryPlan.tileAt(cell.first, cell.second)!!.shape.name.lowercase().replace('_', '-')
                render("pc-gleisbild-editor-$what-$tab-dunkel", 1440, 1100, 1f) {
                    MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId, sound = demoSound)
                }
            }
        }
        pro.setDarkTheme(false)
        TrackPlanUiState.selected.value = 20 to 4
        TrackPlanUiState.accessoryTab.value = 2
        render("handy-gleisbild-editor-schiebebuehne-zubehoer-hell", 824, 2400, 2f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
        }
        TrackPlanUiState.selected.value = null
        TrackPlanUiState.accessoryTab.value = 0
        TrackPlanUiState.editPlan.value = null
        TrackPlanUiState.mode.value = TrackPlanMode.BETRIEB
        TrackPlanUiState.tileSize.value = 24
        proPlansService.toggleVisible(previousVisible.first())
        proPlansService.toggleVisible(accessoryPlan.id)
        proPlansService.removePlan(accessoryPlan.id)

        // Messstrecke, Modellzeit-Uhr und Bearbeiten wie in iTrain
        pro.setDarkTheme(true)
        val measurePlan = proPlansService.addPlan("Messung und Uhr").resize(22, 9).let { start ->
            var p = start
            fun put(t: TrackTile) { p = p.put(t) }
            (0..19).forEach { x -> put(TrackTile(x, 2, TrackShape.STRAIGHT, 2)) }
            put(TrackTile(3, 2, TrackShape.FEEDBACK, 2, feedbackModule = 3, feedbackContact = 14).withProp(AccessoryProps.NAME, "Messung A"))
            put(TrackTile(11, 2, TrackShape.FEEDBACK, 2, feedbackModule = 3, feedbackContact = 15).withProp(AccessoryProps.NAME, "Messung B"))
            put(
                TrackTile(7, 2, TrackShape.MEASUREMENT, 2).withProp(AccessoryProps.NAME, "Messstrecke")
                    .withProp(AccessoryProps.MEASURE_START, "3.14").withProp(AccessoryProps.MEASURE_END, "3.15")
                    .withProp(AccessoryProps.MEASURE_LENGTH, "300"),
            )
            put(TrackTile(16, 2, TrackShape.TURNOUT_RIGHT, 2, address = 40))
            put(TrackTile(4, 0, TrackShape.TEXT, text = "Messstrecke 300 mm"))
            (0..9).forEach { x -> put(TrackTile(x, 6, TrackShape.STRAIGHT, 2)) }
            put(TrackTile(5, 6, TrackShape.TURNOUT_RIGHT, 2, address = 40))
            put(TrackTile(15, 5, TrackShape.CLOCK).withProp(AccessoryProps.SIZE, "3"))
            put(TrackTile(15, 4, TrackShape.TEXT, text = "Bahnhofsuhr"))
            p
        }
        proPlansService.update(measurePlan)
        val visibleBefore = proPlansService.visiblePlans.value
        proPlansService.visiblePlans.value.forEach { if (proPlansService.visiblePlans.value.size > 1) proPlansService.toggleVisible(it) }
        proPlansService.toggleVisible(measurePlan.id)
        proPlansService.visiblePlans.value.filter { it != measurePlan.id }.forEach { proPlansService.toggleVisible(it) }
        proPlansService.setClock(8, 30)
        proPlansService.setClockFactor(6)
        proPlansService.startClock()
        proPlansService.placeTrain(3 to 14, 12)
        proSim.simulateFeedback(3, 14, occupied = true)
        delay(600)
        proSim.simulateFeedback(3, 15, occupied = true)
        delay(100)
        TrackPlanUiState.mode.value = TrackPlanMode.BETRIEB
        TrackPlanUiState.tileSize.value = 36
        render("pc-gleisbild-messung-uhr-dunkel", 1440, 900, 1f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
        }
        TrackPlanUiState.contextMenu.value = measurePlan.id to (7 to 2)
        render("pc-gleisbild-kontextmenue-messung-dunkel", 1440, 900, 1f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
        }
        TrackPlanUiState.contextMenu.value = null
        TrackPlanUiState.clockDialog.value = true
        render("pc-gleisbild-modellzeit-dialog-dunkel", 1440, 900, 1f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
        }
        pro.setDarkTheme(false)
        render("handy-gleisbild-modellzeit-dialog-hell", 824, 1780, 2f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
        }
        TrackPlanUiState.clockDialog.value = false
        render("handy-gleisbild-messung-uhr-hell", 824, 1780, 2f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
        }
        pro.setDarkTheme(true)
        TrackPlanUiState.mode.value = TrackPlanMode.BEARBEITEN
        TrackPlanUiState.editPlan.value = measurePlan.id
        TrackPlanUiState.tool.value = EditTool.Area
        TrackPlanUiState.tileSize.value = 32
        TrackPlanUiState.area.value = (1..5).map { it to 2 }.toSet()
        TrackPlanUiState.clipboard.value = listOf(TrackTile(0, 0, TrackShape.STRAIGHT, 2), TrackTile(1, 0, TrackShape.STRAIGHT, 2))
        render("pc-gleisbild-editor-bereich-dunkel", 1440, 1000, 1f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
        }
        TrackPlanUiState.editorMenu.value = 3 to 2
        render("pc-gleisbild-editor-kontextmenue-dunkel", 1440, 1000, 1f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
        }
        TrackPlanUiState.editorMenu.value = null
        TrackPlanUiState.area.value = emptySet()
        TrackPlanUiState.tool.value = EditTool.Select
        TrackPlanUiState.selected.value = 5 to 6
        render("pc-gleisbild-editor-objekt-wiederverwenden-dunkel", 1440, 1000, 1f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
        }
        TrackPlanUiState.selected.value = 7 to 2
        TrackPlanUiState.accessoryTab.value = 1
        render("pc-gleisbild-editor-messstrecke-messung-dunkel", 1440, 1000, 1f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
        }
        pro.setDarkTheme(false)
        TrackPlanUiState.selected.value = null
        TrackPlanUiState.accessoryTab.value = 0
        TrackPlanUiState.area.value = (1..5).map { it to 2 }.toSet()
        render("handy-gleisbild-editor-bereich-hell", 824, 2400, 2f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId)
        }
        TrackPlanUiState.area.value = emptySet()
        TrackPlanUiState.clipboard.value = emptyList()
        TrackPlanUiState.editPlan.value = null
        TrackPlanUiState.mode.value = TrackPlanMode.BETRIEB
        TrackPlanUiState.tileSize.value = 24
        proPlansService.stopClock()
        proPlansService.trains.value.keys.forEach(proPlansService::removeTrain)
        proSim.simulateFeedback(3, 14, occupied = false)
        proSim.simulateFeedback(3, 15, occupied = false)
        proPlansService.toggleVisible(visibleBefore.first())
        proPlansService.toggleVisible(measurePlan.id)
        proPlansService.removePlan(measurePlan.id)

        // Datei-Menue wie in iTrain: Anlagen, Sicherung, Import/Export, Ressourcen-Browser, Drucken
        val fileRoot = java.nio.file.Files.createTempDirectory("moba-anlage").toFile()
        val fileSettings = mutableMapOf<String, String>()
        val layoutFiles = FolderLayoutFiles(fileRoot, { fileSettings[it] }, { k, v -> fileSettings[k] = v })
        File(fileRoot, "gleisbilder.txt").writeText("x".repeat(18_000))
        File(fileRoot, "fahrzeuge.txt").writeText("x".repeat(4_200))
        File(fileRoot, "fahrstrassen.txt").writeText("x".repeat(2_100))
        File(fileRoot, "fahrzeugbilder").mkdirs()
        File(fileRoot, "fahrzeugbilder/br218.bild").writeBytes(demoLocoPhoto())
        File(fileRoot, "klaenge").mkdirs()
        File(fileRoot, "klaenge/pfiff.wav").writeBytes(ByteArray(48_000))
        layoutFiles.backup("automatisch")
        layoutFiles.create("Clubanlage Nord")
        layoutFiles.open(fileRoot.path)
        layoutFiles.backup()
        val fileSupport = FileSupport(layoutFiles) {}
        pro.setDarkTheme(true)
        TrackPlanUiState.mode.value = TrackPlanMode.BETRIEB
        FileUiState.menuOpen.value = true
        render("pc-datei-menue-dunkel", 1440, 900, 1f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId, files = fileSupport)
        }
        FileUiState.menuOpen.value = false
        for ((kind, name) in listOf(
            FileDialogKind.OPEN to "anlage-oeffnen", FileDialogKind.BACKUP to "sicherung", FileDialogKind.EXPORT to "export",
            FileDialogKind.IMPORT to "import", FileDialogKind.RESOURCES to "ressourcen", FileDialogKind.PRINT to "drucken",
        )) {
            FileUiState.dialog.value = kind
            render("pc-datei-$name-dunkel", 1440, 900, 1f) {
                MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId, files = fileSupport)
            }
        }
        pro.setDarkTheme(false)
        FileUiState.dialog.value = FileDialogKind.RESOURCES
        render("handy-datei-ressourcen-hell", 824, 1780, 2f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId, files = fileSupport)
        }
        FileUiState.dialog.value = null
        FileUiState.menuOpen.value = true
        render("handy-datei-menue-hell", 824, 1780, 2f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId, files = fileSupport)
        }
        FileUiState.menuOpen.value = false

        // Aktionen: eigenes Burger-Menue neben "Datei", Editor mit Bedingungsbaum, Brennstoff
        proVehicles.saveLoco(LocoRecord("fz-lok-50", "BR 50 Kabinentender", 50, "BR 50", "DB", Epoch.III, Traction.STEAM, gauge = Gauge.H0, consist = listOf("fz-gw-4", "fz-gw-1")))
        proVehicles.saveLoco(LocoRecord("fz-lok-5", "V 100", 5, "BR 212", "DB", Epoch.III, Traction.DIESEL, gauge = Gauge.H0))
        proSim.setLocoSpeed(50, 40, Direction.FORWARD)
        proSim.setLocoSpeed(5, 0, Direction.FORWARD)
        delay(500)
        pro.setDarkTheme(true)
        TrackPlanUiState.mode.value = TrackPlanMode.BETRIEB
        ActionsUiState.menuOpen.value = true
        render("pc-aktionen-menue-dunkel", 1440, 900, 1f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId, files = fileSupport, menus = listOf(ActionsMenu))
        }
        ActionsUiState.menuOpen.value = false
        pro.setDarkTheme(false)
        ActionsUiState.window.value = ActionsWindow(ruleId = "ak-3")
        render("pc-aktionen-editor-hell", 1440, 900, 1f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId, files = fileSupport, menus = listOf(ActionsMenu))
        }
        ActionsUiState.window.value = ActionsWindow(ruleId = "ak-2")
        render("handy-aktionen-editor-hell", 824, 1780, 2f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId, files = fileSupport, menus = listOf(ActionsMenu))
        }
        pro.setDarkTheme(true)
        ActionsUiState.window.value = ActionsWindow(ActionsTab.FUEL)
        render("pc-aktionen-brennstoff-dunkel", 1440, 900, 1f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId, files = fileSupport, menus = listOf(ActionsMenu))
        }
        ActionsUiState.window.value = null
        pro.setDarkTheme(false)
        ActionsUiState.menuOpen.value = true
        render("handy-aktionen-menue-hell", 824, 1780, 2f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = TrackPlanScreen.moduleId, files = fileSupport, menus = listOf(ActionsMenu))
        }
        ActionsUiState.menuOpen.value = false
        VehiclesUiState.draft.value = VehicleDraft.Loco(proVehicles.fleet.value.locos.first { it.id == "fz-lok-50" }, isNew = false)
        render("pc-lok-spurweite-wagen-hell", 1440, 1300, 1f) {
            MobaShell(pro, withRoutes, listOf(DrivingPanel), initialScreen = VehiclesScreen.moduleId, menus = listOf(ActionsMenu))
        }
        VehiclesUiState.draft.value = null
        proSim.setLocoSpeed(50, 0, Direction.FORWARD)
        fileRoot.deleteRecursively()

        // Lizenz: erst kostenlos, dann mit einem Testschluessel (eigenes Schluesselpaar nur fuer diesen Test)
        app.setDarkTheme(true)
        ShellUiState.licenseOpen.value = true
        render("pc-lizenz-kostenlos-dunkel", 1440, 900, 1f) { MobaShell(app, withDecoder, listOf(DrivingPanel)) }
        val key = testSigner.sign(License("L-20261013-4711", "Max Mustermann", setOf("pro", "decoder"), "2026-10-13"))
        assertTrue(app.activateLicense(key) == null)
        delay(300)
        render("pc-lizenz-pro-dunkel", 1440, 900, 1f) { MobaShell(app, withDecoder, listOf(DrivingPanel)) }
        app.setDarkTheme(false)
        render("handy-lizenz-pro-hell", 824, 1780, 2f) { MobaShell(app, withDecoder, listOf(DrivingPanel)) }
        ShellUiState.licenseOpen.value = false
        render("pc-uebersicht-lizenz-hell", 1440, 900, 1f) { MobaShell(app, withDecoder, listOf(DrivingPanel)) }

        scope.cancel()
        assertTrue(File(outDir, "pc-fahren-dunkel.png").length() > 0)
    }

    /** Beispielfoto fuer das Wasserzeichen: Abendhimmel mit Gleisen in Fluchtperspektive. */
    /** Ein gezeichnetes "Foto" einer roten Diesellok fuer die Fahrzeugliste. */
    private fun demoLocoPhoto(): ByteArray {
        val w = 800
        val h = 450
        val surface = Surface.makeRasterN32Premul(w, h)
        val canvas = surface.canvas
        canvas.drawRect(
            Rect.makeWH(w.toFloat(), h.toFloat()),
            Paint().apply { shader = Shader.makeLinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xFF9CC8E8.toInt(), 0xFFE8E2D0.toInt())) },
        )
        val paint = Paint().apply { isAntiAlias = true }
        canvas.drawRect(Rect.makeLTRB(0f, 360f, w.toFloat(), 372f), paint.apply { color = 0xFF555555.toInt() })
        canvas.drawRect(Rect.makeLTRB(90f, 170f, 710f, 320f), paint.apply { color = 0xFFB3261E.toInt() })
        canvas.drawRect(Rect.makeLTRB(300f, 120f, 500f, 175f), paint.apply { color = 0xFFB3261E.toInt() })
        canvas.drawRect(Rect.makeLTRB(320f, 132f, 380f, 165f), paint.apply { color = 0xFF2B3A45.toInt() })
        canvas.drawRect(Rect.makeLTRB(420f, 132f, 480f, 165f), paint.apply { color = 0xFF2B3A45.toInt() })
        canvas.drawRect(Rect.makeLTRB(90f, 300f, 710f, 312f), paint.apply { color = 0xFFF2F2F2.toInt() })
        for (x in listOf(170f, 250f, 550f, 630f)) canvas.drawCircle(x, 335f, 30f, paint.apply { color = 0xFF222222.toInt() })
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
    }

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
