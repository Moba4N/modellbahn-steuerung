package de.moba.core.runtime

import de.moba.core.model.LayoutRepository
import de.moba.core.station.CommandStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

enum class ModuleState { INSTALLED, LOCKED, ACTIVE, FAILED, STOPPED }

data class ModuleStatus(val state: ModuleState, val reason: String? = null)

/**
 * Laedt, prueft und startet die Module.
 *
 * Lizenzen werden nur beim Start geprueft. Ein laufendes Modul wird nie wegen eines
 * abgelaufenen Abos angehalten, damit der Fahrbetrieb nicht mittendrin abbricht.
 */
class ModuleHost(
    private val station: CommandStation,
    private val layout: LayoutRepository,
    private val license: LicenseInfo,
    private val scope: CoroutineScope,
    val events: EventBus = EventBus(),
) {
    private val modules = linkedMapOf<String, MobaModule>()
    private val moduleScopes = mutableMapOf<String, CoroutineScope>()
    private val services = mutableMapOf<KClass<*>, Any>()
    private var started = mutableListOf<MobaModule>()
    private var forwarding: Job? = null

    private val _status = MutableStateFlow<Map<String, ModuleStatus>>(emptyMap())
    val status: StateFlow<Map<String, ModuleStatus>> = _status

    fun register(module: MobaModule) {
        require(module.id !in modules) { "Modul ${module.id} ist schon registriert" }
        modules[module.id] = module
        setStatus(module.id, ModuleStatus(ModuleState.INSTALLED))
    }

    fun register(vararg modules: MobaModule) = modules.forEach(::register)

    fun module(id: String): MobaModule? = modules[id]

    /** Startet alle Module in Abhaengigkeitsreihenfolge, soweit Lizenz und Abhaengigkeiten es erlauben. */
    suspend fun startAll() {
        if (forwarding == null) {
            // Rueckmeldungen der Zentrale fuer alle Module auf den Ereignis-Bus legen.
            forwarding = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                station.events.collect { events.publish(it) }
            }
        }
        val (ordered, cyclic) = sortByDependencies()
        cyclic.forEach { setStatus(it, ModuleStatus(ModuleState.FAILED, "Zyklische Abhaengigkeit")) }
        for (module in ordered) {
            if (state(module.id) == ModuleState.ACTIVE) continue
            val blocker = checkStartable(module)
            if (blocker != null) {
                setStatus(module.id, blocker)
                continue
            }
            val moduleScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
            try {
                module.start(Context(moduleScope))
                moduleScopes[module.id] = moduleScope
                started += module
                setStatus(module.id, ModuleStatus(ModuleState.ACTIVE))
            } catch (e: Exception) {
                moduleScope.cancel()
                setStatus(module.id, ModuleStatus(ModuleState.FAILED, e.message ?: e::class.simpleName))
            }
        }
    }

    /** Stoppt alle laufenden Module in umgekehrter Startreihenfolge. */
    suspend fun stopAll() {
        for (module in started.asReversed()) {
            runCatching { module.stop() }
            moduleScopes.remove(module.id)?.cancel()
            setStatus(module.id, ModuleStatus(ModuleState.STOPPED))
        }
        started = mutableListOf()
        services.clear()
        forwarding?.cancel()
        forwarding = null
    }

    private fun checkStartable(module: MobaModule): ModuleStatus? {
        for (dep in module.requires) {
            val other = modules[dep.moduleId]
                ?: return ModuleStatus(ModuleState.FAILED, "Benoetigtes Modul fehlt: ${dep.moduleId}")
            if (!other.apiVersion.satisfies(dep.minApiVersion)) {
                return ModuleStatus(
                    ModuleState.FAILED,
                    "${dep.moduleId} hat API ${other.apiVersion}, benoetigt wird ${dep.minApiVersion}",
                )
            }
            when (state(dep.moduleId)) {
                ModuleState.ACTIVE -> Unit
                ModuleState.LOCKED -> return ModuleStatus(ModuleState.LOCKED, "Benoetigt ${dep.moduleId}")
                else -> return ModuleStatus(ModuleState.FAILED, "${dep.moduleId} laeuft nicht")
            }
        }
        val key = module.licenseKey
        if (key != null && !license.isUnlocked(key)) {
            return ModuleStatus(ModuleState.LOCKED, "Abo \"$key\" nicht freigeschaltet")
        }
        return null
    }

    /** Kahn-Algorithmus; liefert die Startreihenfolge und alle Module in Zyklen. */
    private fun sortByDependencies(): Pair<List<MobaModule>, List<String>> {
        val pending = modules.values.associateWith { m -> m.requires.map { it.moduleId }.filter { it in modules } }
            .toMutableMap()
        val ordered = mutableListOf<MobaModule>()
        while (pending.isNotEmpty()) {
            val ready = pending.filterValues { deps -> deps.none { id -> pending.keys.any { it.id == id } } }.keys
            if (ready.isEmpty()) break
            ordered += ready
            ready.forEach(pending::remove)
        }
        return ordered to pending.keys.map { it.id }
    }

    private fun state(id: String) = _status.value[id]?.state

    private fun setStatus(id: String, status: ModuleStatus) = _status.update { it + (id to status) }

    private inner class Context(override val scope: CoroutineScope) : ModuleContext {
        override val events get() = this@ModuleHost.events
        override val station get() = this@ModuleHost.station
        override val layout get() = this@ModuleHost.layout
        override val license get() = this@ModuleHost.license

        override fun <T : Any> provide(type: KClass<T>, service: T) {
            services[type] = service
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> service(type: KClass<T>): T? = services[type] as T?
    }
}
