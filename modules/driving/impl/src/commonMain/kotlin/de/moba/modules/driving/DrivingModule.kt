package de.moba.modules.driving

import de.moba.core.runtime.LicenseInfo
import de.moba.core.runtime.MobaModule
import de.moba.core.runtime.ModuleContext
import de.moba.core.runtime.SemVer
import de.moba.core.runtime.on
import de.moba.core.runtime.provide
import de.moba.core.station.CommandStation
import de.moba.core.station.Direction
import de.moba.core.station.LocoState
import de.moba.core.station.StationEvent
import de.moba.modules.driving.api.DRIVING_MODULE_ID
import de.moba.modules.driving.api.DrivingService
import de.moba.modules.driving.api.LocoLimitReachedException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Modul "Fahren" (M1): Loks fahren und Funktionen schalten. Kostenlos bis [FREE_LOCO_LIMIT] Loks. */
class DrivingModule : MobaModule {
    override val id = DRIVING_MODULE_ID
    override val displayName = "Fahren"
    override val version = SemVer(1, 0)
    override val apiVersion = SemVer(1, 0)
    override val licenseKey: String? = null

    override suspend fun start(ctx: ModuleContext) {
        val service = DrivingServiceImpl(ctx.station, ctx.license)
        ctx.events.on<StationEvent.LocoChanged>(ctx.scope) { service.onLocoChanged(it.state) }
        ctx.provide<DrivingService>(service)
    }

    companion object {
        const val FREE_LOCO_LIMIT = 3
        const val UNLIMITED_LICENSE_KEY = "basis"
    }
}

internal class DrivingServiceImpl(
    private val station: CommandStation,
    license: LicenseInfo,
) : DrivingService {
    private val _locos = MutableStateFlow<Map<Int, LocoState>>(emptyMap())
    override val locos: StateFlow<Map<Int, LocoState>> = _locos

    override val locoLimit: Int? =
        if (license.isUnlocked(DrivingModule.UNLIMITED_LICENSE_KEY)) null else DrivingModule.FREE_LOCO_LIMIT

    private val controlled = mutableSetOf<Int>()

    override suspend fun drive(address: Int, speed: Int, direction: Direction) {
        claim(address)
        station.setLocoSpeed(address, speed, direction)
    }

    override suspend fun setFunction(address: Int, function: Int, on: Boolean) {
        claim(address)
        station.setLocoFunction(address, function, on)
    }

    override suspend fun stop(address: Int) {
        val direction = _locos.value[address]?.direction ?: Direction.FORWARD
        station.setLocoSpeed(address, 0, direction)
    }

    fun onLocoChanged(state: LocoState) {
        // Auch Loks, die ein Handregler an der Zentrale faehrt, werden angezeigt.
        _locos.update { it + (state.address to state) }
    }

    private fun claim(address: Int) {
        val limit = locoLimit
        if (limit != null && address !in controlled && controlled.size >= limit) throw LocoLimitReachedException(limit)
        controlled += address
    }
}
