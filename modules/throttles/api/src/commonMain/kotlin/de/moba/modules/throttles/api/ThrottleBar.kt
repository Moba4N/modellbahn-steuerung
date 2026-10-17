package de.moba.modules.throttles.api

import kotlinx.coroutines.flow.StateFlow

const val THROTTLES_MODULE_ID = "moba.fahrregler"

/** Merkt sich, welche Fahrregler in der Leiste offen sind (Lokadressen in Reihenfolge). */
interface ThrottleBarStore {
    fun load(): List<Int>
    fun save(addresses: List<Int>)
}

/**
 * Oeffentliche Schnittstelle des Moduls "Mehrere Fahrregler": eine Leiste am unteren Bildrand
 * mit bis zu [maxThrottles] Fahrreglern gleichzeitig. Gefahren wird ueber das Modul "Fahren".
 */
interface ThrottleBarService {
    /** Adressen der offenen Fahrregler, von links nach rechts. */
    val open: StateFlow<List<Int>>
    val maxThrottles: Int

    /** Oeffnet einen Fahrregler; ist die Lok schon offen, passiert nichts. @throws IllegalStateException wenn die Leiste voll ist */
    fun open(address: Int)
    fun close(address: Int)
}
