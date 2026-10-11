package de.moba.sandbox

import de.moba.adapters.simulator.SimulatorStation
import de.moba.core.model.FeedbackContact
import de.moba.core.model.InMemoryLayoutRepository
import de.moba.core.model.Layout
import de.moba.core.model.Loco
import de.moba.core.model.TrackSection
import de.moba.core.runtime.Dependency
import de.moba.core.runtime.LicenseInfo
import de.moba.core.runtime.MobaModule
import de.moba.core.runtime.ModuleContext
import de.moba.core.runtime.ModuleHost
import de.moba.core.runtime.SemVer
import de.moba.core.runtime.on
import de.moba.core.station.Direction
import de.moba.core.station.StationEvent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Sandbox: startet Kern, Simulator und Beispielmodule ohne Oberflaeche und ohne Hardware.
 * So laesst sich ein Modul isoliert entwickeln. Aufruf: ./gradlew :tools:sandbox:run
 * Mit --args="z21 <IP-Adresse>" stattdessen Verbindungstest mit einer echten Zentrale.
 */
fun main(args: Array<String>) = runBlocking {
    if (args.size == 2 && args[0] == "z21") return@runBlocking probeZ21(args[1])
    coroutineScope {
        val sim = SimulatorStation(name = "Simulator (statt YaMoRC YD7001)")
        val layout = InMemoryLayoutRepository(
            Layout(
                name = "Testanlage",
                locos = listOf(Loco("br218", "BR 218", address = 3)),
                sections = listOf(TrackSection("b1", "Bahnhof Gleis 1")),
                feedback = listOf(FeedbackContact(module = 2, contact = 5, sectionId = "b1")),
            ),
        )
        // Basis-Abo freigeschaltet, Pro nicht.
        val host = ModuleHost(sim, layout, LicenseInfo.of("basis"), this)
        host.register(DemoDriving(), DemoFeedback(), DemoAutomation())

        sim.connect()
        sim.setTrackPower(true)
        host.startAll()

        println("Module:")
        host.status.value.forEach { (id, s) -> println("  $id: ${s.state}${s.reason?.let { " ($it)" } ?: ""}") }

        sim.simulateFeedback(module = 2, contact = 5, occupied = true)
        delay(100)
        sim.emergencyStop()
        delay(100)

        host.stopAll()
        println("Fertig.")
    }
}

/** Beispiel fuer ein kostenloses Modul: faehrt Lok 3 an. */
private class DemoDriving : MobaModule {
    override val id = "demo.fahren"
    override val version = SemVer(0, 1)
    override val apiVersion = SemVer(1)
    override val licenseKey: String? = null

    override suspend fun start(ctx: ModuleContext) {
        ctx.events.on<StationEvent.LocoChanged>(ctx.scope) { println("  [fahren] Lok ${it.state.address}: Fahrstufe ${it.state.speed}") }
        val loco = ctx.layout.layout.value.locos.first()
        ctx.station.setLocoSpeed(loco.address, 40, Direction.FORWARD)
    }
}

/** Beispiel fuer ein Basis-Modul: zeigt s88-Belegung mit dem Namen des Gleisabschnitts. */
private class DemoFeedback : MobaModule {
    override val id = "demo.melder"
    override val version = SemVer(0, 1)
    override val apiVersion = SemVer(1)
    override val licenseKey = "basis"
    override val requires = listOf(Dependency("demo.fahren", SemVer(1)))

    override suspend fun start(ctx: ModuleContext) {
        ctx.events.on<StationEvent.FeedbackChanged>(ctx.scope) {
            val section = ctx.layout.layout.value.sectionFor(it.module, it.contact)?.name ?: "nicht zugeordnet"
            println("  [melder] s88 ${it.module}.${it.contact} ${if (it.occupied) "belegt" else "frei"}: $section")
        }
        ctx.events.on<StationEvent.EmergencyStopped>(ctx.scope) { println("  [melder] NOTHALT") }
    }
}

/** Beispiel fuer ein Pro-Modul: bleibt ohne Pro-Abo gesperrt. */
private class DemoAutomation : MobaModule {
    override val id = "demo.automatik"
    override val version = SemVer(0, 1)
    override val apiVersion = SemVer(1)
    override val licenseKey = "pro"
    override val requires = listOf(Dependency("demo.melder", SemVer(1)))

    override suspend fun start(ctx: ModuleContext) = Unit
}
