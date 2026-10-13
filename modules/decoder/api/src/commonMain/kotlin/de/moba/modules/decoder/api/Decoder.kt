package de.moba.modules.decoder.api

import kotlinx.coroutines.flow.StateFlow

const val DECODER_MODULE_ID = "moba.decoder"

/** Wo programmiert wird: auf dem Programmiergleis (lesen und schreiben) oder waehrend der Fahrt (nur schreiben). */
enum class ProgrammingTrack(val label: String) {
    SERVICE("Programmiergleis"),
    MAIN("Hauptgleis (POM)"),
}

/** Eine CV mit Bedeutung nach NMRA-Norm. Herstellerspezifische CVs traegt man selbst ein. */
data class CvInfo(val cv: Int, val name: String, val hint: String = "")

object StandardCvs {
    val all = listOf(
        CvInfo(1, "Kurze Adresse", "1 bis 127"),
        CvInfo(2, "Anfahrspannung"),
        CvInfo(3, "Beschleunigung"),
        CvInfo(4, "Bremszeit"),
        CvInfo(5, "Höchstgeschwindigkeit"),
        CvInfo(6, "Mittengeschwindigkeit"),
        CvInfo(7, "Versionsnummer", "nur lesen"),
        CvInfo(8, "Herstellerkennung", "nur lesen; 8 schreiben setzt viele Decoder zurück"),
        CvInfo(17, "Lange Adresse, oberes Byte"),
        CvInfo(18, "Lange Adresse, unteres Byte"),
        CvInfo(19, "Mehrfachtraktion"),
        CvInfo(29, "Konfiguration"),
    )

    fun info(cv: Int) = all.firstOrNull { it.cv == cv }

    /** Die CVs, die "Decoder auslesen" holt. */
    val readSet = all.map { it.cv }
}

/** Herstellerkennungen in CV 8 nach NMRA-Liste (Auswahl). */
object DecoderManufacturers {
    private val names = mapOf(
        62 to "Tams",
        85 to "Uhlenbrock",
        97 to "Doehler & Haass",
        99 to "Lenz",
        129 to "Digitrax",
        131 to "Märklin",
        145 to "Zimo",
        151 to "ESU",
        157 to "Kühn",
    )

    fun name(id: Int): String = names[id] ?: "Hersteller $id"
}

/** Die Bits der Konfigurations-CV 29. */
enum class Cv29Bit(val bit: Int, val label: String) {
    REVERSED(0, "Fahrtrichtung umgekehrt"),
    SPEED_STEPS(1, "28/128 Fahrstufen (sonst 14)"),
    ANALOG(2, "Analogbetrieb erlaubt"),
    RAILCOM(3, "RailCom"),
    SPEED_TABLE(4, "Eigene Geschwindigkeitskennlinie"),
    LONG_ADDRESS(5, "Lange Adresse verwenden"),
    ;

    fun isSet(cv29: Int) = cv29 and (1 shl bit) != 0
    fun set(cv29: Int, on: Boolean) = if (on) cv29 or (1 shl bit) else cv29 and (1 shl bit).inv()
}

/** Umrechnung der Lokadresse in CVs (kurze Adresse in CV 1, lange in CV 17/18 plus Bit 5 in CV 29). */
object DecoderAddress {
    const val MAX_SHORT = 127
    const val MAX_LONG = 10239

    /** Welche CVs fuer [address] zu schreiben sind; [cv29] ist der bisherige Wert. */
    fun cvsFor(address: Int, cv29: Int): Map<Int, Int> {
        require(address in 1..MAX_LONG) { "Die Adresse muss zwischen 1 und $MAX_LONG liegen." }
        return if (address <= MAX_SHORT) {
            mapOf(1 to address, 29 to Cv29Bit.LONG_ADDRESS.set(cv29, false))
        } else {
            mapOf(17 to (0xC0 or (address shr 8)), 18 to (address and 0xFF), 29 to Cv29Bit.LONG_ADDRESS.set(cv29, true))
        }
    }

    /** Die wirksame Adresse aus gelesenen CVs, null wenn etwas fehlt. */
    fun from(cvs: Map<Int, Int>): Int? {
        val cv29 = cvs[29] ?: return cvs[1]
        return if (Cv29Bit.LONG_ADDRESS.isSet(cv29)) {
            val hi = cvs[17] ?: return null
            val lo = cvs[18] ?: return null
            ((hi and 0x3F) shl 8) or lo
        } else {
            cvs[1]
        }
    }
}

/**
 * Eine gespeicherte Decoder-Programmierung, z. B. "BR 218 – ESU LokSound 5". [locoAddress] und
 * [locoName] verbinden sie mit einer Lok; [cvs] enthaelt alle gesicherten Werte.
 */
data class DecoderProfile(
    val id: String,
    val name: String,
    val locoName: String = "",
    val locoAddress: Int? = null,
    val decoderType: String = "",
    val cvs: Map<Int, Int> = emptyMap(),
    val notes: String = "",
) {
    val manufacturer: String? get() = cvs[8]?.let(DecoderManufacturers::name)
}

interface DecoderStore {
    fun load(): List<DecoderProfile>
    fun save(profiles: List<DecoderProfile>)
}

/**
 * Oeffentliche Schnittstelle des Moduls "Decoder". CVs lesen und schreiben ist kostenlos;
 * die Decoder-Datenbank ([saveProfile]) braucht die Zusatzlizenz.
 */
interface DecoderService {
    /** Kann die verbundene Zentrale Decoder programmieren? */
    val canProgram: Boolean

    /** Liest eine CV auf dem Programmiergleis. @throws de.moba.core.station.CvException */
    suspend fun read(cv: Int): Int

    /** Schreibt eine CV; mit [mainAddress] waehrend der Fahrt auf dem Hauptgleis. */
    suspend fun write(cv: Int, value: Int, mainAddress: Int? = null)

    val profiles: StateFlow<List<DecoderProfile>>

    /** Darf die Decoder-Datenbank genutzt werden? */
    val canUseDatabase: Boolean

    /** @throws DecoderLicenseException ohne Zusatzlizenz */
    fun saveProfile(profile: DecoderProfile): DecoderProfile
    fun deleteProfile(id: String)
    fun newProfileId(): String
}

class DecoderLicenseException(message: String) : Exception(message)
