package de.moba.modules.routes

import de.moba.core.model.LocoDynamics
import de.moba.core.runtime.ModuleContext
import de.moba.modules.calibration.api.CalibrationService
import de.moba.modules.calibration.api.Scale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Wie lange die Automatik nach dem Haltemelder noch faehrt: Rueckmelde-Offset geteilt durch die
 * Modellgeschwindigkeit, abzueglich der Reaktionsverzoegerung des Decoders.
 */
internal data class StopTiming(val dynamics: LocoDynamics, val mmPerSecond: Double?) {
    fun delayMillis(): Long {
        val v = mmPerSecond ?: return 0
        if (dynamics.feedbackOffsetMm <= 0 || v <= 0.0) return 0
        return max(0L, (dynamics.feedbackOffsetMm / v * 1000).roundToLong() - dynamics.reactionDelayMs)
    }
}

/** km/h des Vorbilds in Modell-mm/s umrechnen. */
internal fun modelMmPerSecond(kmh: Double, scale: Scale): Double = kmh / 3.6 * 1000.0 / scale.factor

/**
 * Fahreigenschaften der Lok aus dem Anlagenmodell; die Geschwindigkeit kommt aus dem Einmessen,
 * sonst geschaetzt aus der Hoechstgeschwindigkeit (gleichmaessig ueber 126 Fahrstufen, H0).
 */
internal fun stopTiming(ctx: ModuleContext, address: Int, speed: Int): StopTiming? {
    val loco = ctx.layout.layout.value.locos.firstOrNull { it.address == address } ?: return null
    val d = loco.dynamics.validated()
    if (d.feedbackOffsetMm <= 0) return null
    val profile = ctx.service(CalibrationService::class)?.profiles?.value?.firstOrNull { it.address == address }
    val measured = profile?.let { p ->
        val step = speed * p.mode.steps / 126.0
        p.points.filter { it.kmh != null }.minByOrNull { abs(it.step - step) }?.kmh?.let { modelMmPerSecond(it, p.scale) }
    }
    val estimated = loco.vmaxKmh?.let { modelMmPerSecond(it * speed / 126.0, Scale.H0) }
    return StopTiming(d, measured ?: estimated)
}
