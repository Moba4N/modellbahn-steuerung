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

    /** Lok normal anhalten (bremsen), Richtung bleibt. Nie durch ein Limit gesperrt. */
    suspend fun stop(address: Int)
}

class LocoLimitReachedException(val limit: Int) :
    Exception("Ohne Basis-Abo lassen sich $limit Loks steuern. Mit dem Basis-Abo sind es beliebig viele.")
