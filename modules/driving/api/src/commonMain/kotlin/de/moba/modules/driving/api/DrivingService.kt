package de.moba.modules.driving.api

import de.moba.core.station.Direction
import de.moba.core.station.LocoState
import kotlinx.coroutines.flow.StateFlow

const val DRIVING_MODULE_ID = "moba.fahren"

/**
 * Oeffentliche Schnittstelle des Moduls "Fahren" fuer andere Module (z. B. Automatik).
 * Abrufbar mit `ctx.service<DrivingService>()`.
 */
interface DrivingService {
    /** Von der Zentrale bestaetigter Zustand aller gesteuerten Loks, nach Adresse. */
    val locos: StateFlow<Map<Int, LocoState>>

    /** Wie viele Loks ohne Abo gesteuert werden duerfen; `null` = unbegrenzt. */
    val locoLimit: Int?

    /** @throws LocoLimitReachedException wenn ohne Abo schon [locoLimit] andere Loks gesteuert werden. */
    suspend fun drive(address: Int, speed: Int, direction: Direction)
    suspend fun setFunction(address: Int, function: Int, on: Boolean)

    /**
     * Lok normal anhalten, Richtung bleibt. Nie durch ein Limit gesperrt.
     * Mit Traegheitssimulation bremst sie in der eingestellten Bremszeit (Betriebsbremse).
     */
    suspend fun stop(address: Int)

    /** Schnellbremse: sofort Fahrstufe 0, auch mit Traegheitssimulation. Nie durch ein Limit gesperrt. */
    suspend fun emergencyBrake(address: Int)

    /**
     * Fahrhebel der Loks mit Traegheitssimulation: Ziel-Fahrstufe, auf die die Lok gerade zulaeuft.
     * Ist sie erreicht, verschwindet die Lok aus der Liste.
     */
    val targets: StateFlow<Map<Int, Int>>
}

class LocoLimitReachedException(val limit: Int) :
    Exception("Ohne Basis-Abo lassen sich $limit Loks steuern. Mit dem Basis-Abo sind es beliebig viele.")
