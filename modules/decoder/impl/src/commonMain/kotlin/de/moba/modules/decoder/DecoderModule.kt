package de.moba.modules.decoder

import de.moba.core.runtime.LicenseInfo
import de.moba.core.runtime.MobaModule
import de.moba.core.runtime.ModuleContext
import de.moba.core.runtime.SemVer
import de.moba.core.runtime.provide
import de.moba.core.station.CommandStation
import de.moba.core.station.CvException
import de.moba.core.station.CvProgrammer
import de.moba.modules.decoder.api.DECODER_MODULE_ID
import de.moba.modules.decoder.api.DecoderLicenseException
import de.moba.modules.decoder.api.DecoderProfile
import de.moba.modules.decoder.api.DecoderService
import de.moba.modules.decoder.api.DecoderStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Haelt die Decoder-Datenbank ueber Zentralenwechsel hinweg; jede Aenderung wird sofort gespeichert. */
class DecoderRepository(private val store: DecoderStore?) {
    internal val profiles = MutableStateFlow(runCatching { store?.load() }.getOrNull() ?: emptyList())

    internal fun change(transform: (List<DecoderProfile>) -> List<DecoderProfile>) {
        val next = transform(profiles.value)
        store?.save(next)
        profiles.value = next
    }
}

/**
 * Modul "Decoder" (M5): CVs lesen und schreiben ist kostenlos.
 * Die Decoder-Datenbank, in der man Programmierungen je Lok sichert, braucht die Zusatzlizenz [DATABASE_LICENSE_KEYS].
 */
class DecoderModule(private val repository: DecoderRepository) : MobaModule {
    override val id = DECODER_MODULE_ID
    override val displayName = "Decoder"
    override val version = SemVer(1, 0)
    override val apiVersion = SemVer(1, 0)
    override val licenseKey: String? = null

    override suspend fun start(ctx: ModuleContext) {
        ctx.provide<DecoderService>(DecoderServiceImpl(repository, ctx.station, ctx.license))
    }

    companion object {
        /** Zusatzlizenz "Decoder-Datenbank"; im Club-Abo enthalten. */
        val DATABASE_LICENSE_KEYS = listOf("decoder", "club")
    }
}

internal class DecoderServiceImpl(
    private val repository: DecoderRepository,
    station: CommandStation,
    license: LicenseInfo,
) : DecoderService {
    private val programmer = station as? CvProgrammer
    private val busy = Mutex()

    override val canProgram = programmer != null
    override val canUseDatabase = DecoderModule.DATABASE_LICENSE_KEYS.any(license::isUnlocked)
    override val profiles: StateFlow<List<DecoderProfile>> = repository.profiles

    private fun programmer() =
        programmer ?: throw CvException("Diese Zentrale unterstützt das Programmieren in der App noch nicht.")

    override suspend fun read(cv: Int): Int = busy.withLock { programmer().readCv(cv) }

    override suspend fun write(cv: Int, value: Int, mainAddress: Int?) = busy.withLock {
        val p = programmer()
        if (mainAddress == null) p.writeCv(cv, value) else p.writeCvOnMain(mainAddress, cv, value)
    }

    override fun saveProfile(profile: DecoderProfile): DecoderProfile {
        if (!canUseDatabase) {
            throw DecoderLicenseException("Die Decoder-Datenbank gibt es mit der Zusatzlizenz „Decoder-Datenbank“.")
        }
        require(profile.name.isNotBlank()) { "Bitte einen Namen eingeben." }
        repository.change { list ->
            if (list.any { it.id == profile.id }) list.map { if (it.id == profile.id) profile else it } else list + profile
        }
        return profile
    }

    override fun deleteProfile(id: String) = repository.change { list -> list.filterNot { it.id == id } }

    override fun newProfileId(): String {
        val used = profiles.value.map { it.id }.toSet()
        return generateSequence(1) { it + 1 }.map { "decoder-$it" }.first { it !in used }
    }
}
