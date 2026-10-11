package de.moba.sandbox

import de.moba.adapters.z21.z21Station
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Verbindungstest mit einer echten Zentrale: zeigt 60 Sekunden lang alle Meldungen,
 * z. B. ob s88-Belegtmelder ueber das Z21-Protokoll ankommen.
 * Aufruf: ./gradlew :tools:sandbox:run --args="z21 192.168.0.111"
 */
suspend fun probeZ21(host: String) = coroutineScope {
    val station = z21Station(host)
    val printer = launch(start = CoroutineStart.UNDISPATCHED) { station.events.collect { println("  $it") } }
    println("Verbinde mit $host ...")
    station.connect()
    println("Verbunden. Belege jetzt einen s88-Abschnitt; Meldungen erscheinen hier (60 s).")
    delay(60_000)
    station.disconnect()
    printer.cancel()
}
