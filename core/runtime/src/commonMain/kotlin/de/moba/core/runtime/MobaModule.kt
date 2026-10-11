package de.moba.core.runtime

import de.moba.core.model.LayoutRepository
import de.moba.core.station.CommandStation
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KClass

/**
 * Vertrag, den jedes Fachmodul erfuellt (siehe Designdokument, Abschnitt 5).
 * Module sprechen nur ueber [ModuleContext] miteinander: Ereignisse und Dienste.
 */
interface MobaModule {
    /** Eindeutige Kennung, z. B. "moba.fahrstrassen". */
    val id: String
    val version: SemVer
    val apiVersion: SemVer
    val requires: List<Dependency> get() = emptyList()

    /** Abo-Schluessel, z. B. "basis" oder "pro". `null` = immer frei. */
    val licenseKey: String?

    /** Anzeigename fuer die Oberflaeche. */
    val displayName: String get() = id

    /** Dienste registrieren und Ereignisse abonnieren. */
    suspend fun start(ctx: ModuleContext)

    /** Aufraeumen und Zustand sichern. Der Modul-Scope wird danach beendet. */
    suspend fun stop() {}
}

interface ModuleContext {
    val events: EventBus
    val station: CommandStation
    val layout: LayoutRepository
    val license: LicenseInfo

    /** Lebt so lange wie das Modul; wird beim Stoppen beendet. */
    val scope: CoroutineScope

    fun <T : Any> provide(type: KClass<T>, service: T)
    fun <T : Any> service(type: KClass<T>): T?
}

inline fun <reified T : Any> ModuleContext.provide(service: T) = provide(T::class, service)
inline fun <reified T : Any> ModuleContext.service(): T? = service(T::class)

/** Auskunft, welche Abo-Schluessel freigeschaltet sind. Die Anbindung an den Lizenzserver folgt in Phase 3. */
fun interface LicenseInfo {
    fun isUnlocked(licenseKey: String): Boolean

    companion object {
        /** Nur kostenlose Module. */
        val FREE = LicenseInfo { false }

        /** Fuer Entwicklung und Tests: feste Liste freigeschalteter Schluessel. */
        fun of(vararg keys: String): LicenseInfo = keys.toSet().let { set -> LicenseInfo { it in set } }
    }
}
