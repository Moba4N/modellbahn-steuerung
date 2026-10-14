package de.moba.modules.throttles

import de.moba.core.runtime.Dependency
import de.moba.core.runtime.MobaModule
import de.moba.core.runtime.ModuleContext
import de.moba.core.runtime.SemVer
import de.moba.core.runtime.provide
import de.moba.modules.driving.api.DRIVING_MODULE_ID
import de.moba.modules.throttles.api.THROTTLES_MODULE_ID
import de.moba.modules.throttles.api.ThrottleBarService
import de.moba.modules.throttles.api.ThrottleBarStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Haelt die offenen Fahrregler ueber Zentralenwechsel hinweg und speichert jede Aenderung. */
class ThrottleBarRepository(private val store: ThrottleBarStore?) {
    internal val open = MutableStateFlow(
        runCatching { store?.load() }.getOrNull().orEmpty().distinct().take(ThrottleBarModule.MAX_THROTTLES),
    )

    internal fun set(addresses: List<Int>) {
        open.value = addresses
        runCatching { store?.save(addresses) }
    }
}

/**
 * Modul "Mehrere Fahrregler": Leiste am unteren Bildrand mit mehreren Loks gleichzeitig.
 * Gibt es ab Pro; braucht das Modul "Fahren".
 */
class ThrottleBarModule(private val repository: ThrottleBarRepository) : MobaModule {
    override val id = THROTTLES_MODULE_ID
    override val displayName = "Mehrere Fahrregler"
    override val version = SemVer(1, 0)
    override val apiVersion = SemVer(1, 0)
    override val requires = listOf(Dependency(DRIVING_MODULE_ID, SemVer(1, 0)))
    override val licenseKey: String = LICENSE_KEY

    override suspend fun start(ctx: ModuleContext) {
        ctx.provide<ThrottleBarService>(ThrottleBarServiceImpl(repository))
    }

    companion object {
        /** Ab Pro (Club enthaelt Pro). */
        const val LICENSE_KEY = "pro"
        const val MAX_THROTTLES = 6
    }
}

internal class ThrottleBarServiceImpl(private val repository: ThrottleBarRepository) : ThrottleBarService {
    override val open: StateFlow<List<Int>> = repository.open
    override val maxThrottles = ThrottleBarModule.MAX_THROTTLES

    override fun open(address: Int) {
        require(address in 1..10239) { "Die Adresse muss zwischen 1 und 10239 liegen." }
        val current = repository.open.value
        if (address in current) return
        check(current.size < maxThrottles) { "Es lassen sich höchstens $maxThrottles Fahrregler gleichzeitig öffnen." }
        repository.set(current + address)
    }

    override fun close(address: Int) {
        repository.set(repository.open.value - address)
    }
}
