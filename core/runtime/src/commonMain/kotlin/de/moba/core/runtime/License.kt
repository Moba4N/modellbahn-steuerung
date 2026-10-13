package de.moba.core.runtime

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Eine gekaufte Lizenz. [features] sind die freigeschalteten Abo-Schluessel, z. B. "pro", "club", "decoder".
 * [expires] im Format JJJJ-MM-TT; leer = unbefristet.
 */
data class License(
    val id: String,
    val holder: String,
    val features: Set<String>,
    val issued: String,
    val expires: String = "",
) {
    /** Lesbare Namen der Freischaltungen fuer die Anzeige. */
    val featureLabels: List<String> get() = features.sorted().map { FEATURE_LABELS[it] ?: it }

    companion object {
        val FEATURE_LABELS = mapOf(
            "pro" to "Pro",
            "club" to "Club",
            "decoder" to "Decoder-Datenbank",
        )
    }
}

/** Ergebnis der Pruefung eines eingegebenen Lizenzschluessels. */
sealed interface LicenseCheck {
    data class Valid(val license: License) : LicenseCheck
    data class Invalid(val reason: String) : LicenseCheck
}

/** Prueft die Unterschrift eines Lizenzschluessels; die Umsetzung liegt im JVM-Teil (Windows und Android). */
fun interface LicenseVerifier {
    /** @param today heutiges Datum als JJJJ-MM-TT, fuer das Ablaufdatum */
    fun check(key: String, today: String): LicenseCheck
}

/** Freischaltungen aus einer gueltigen Lizenz, zusaetzlich zu [base]. */
fun LicenseInfo.plus(license: License?): LicenseInfo {
    if (license == null) return this
    val base = this
    return LicenseInfo { key -> base.isUnlocked(key) || key in license.features }
}

/**
 * Aufbau eines Lizenzschluessels: `MOBA1-<Inhalt>.<Unterschrift>`, beides Base64 (URL-sicher, ohne `=`).
 * Der Inhalt ist Text mit einer Angabe je Zeile (`name=wert`), die Unterschrift ECDSA P-256 mit SHA-256.
 */
@OptIn(ExperimentalEncodingApi::class)
object LicenseFormat {
    const val PREFIX = "MOBA1-"
    private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    fun payload(license: License): String = buildString {
        appendLine("id=${clean(license.id)}")
        appendLine("name=${clean(license.holder)}")
        appendLine("features=${license.features.sorted().joinToString(",") { clean(it) }}")
        appendLine("issued=${clean(license.issued)}")
        appendLine("expires=${clean(license.expires)}")
    }

    fun parsePayload(text: String): License? {
        val fields = text.lines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
        val id = fields["id"]?.takeIf { it.isNotBlank() } ?: return null
        val features = fields["features"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (features.isEmpty()) return null
        return License(
            id = id,
            holder = fields["name"].orEmpty(),
            features = features,
            issued = fields["issued"].orEmpty(),
            expires = fields["expires"].orEmpty(),
        )
    }

    fun encode(payload: String, signature: ByteArray): String =
        PREFIX + base64.encode(payload.encodeToByteArray()) + "." + base64.encode(signature)

    /** Zerlegt einen Schluessel in Inhalt und Unterschrift; Leerzeichen und Zeilenumbrueche beim Einfuegen werden ignoriert. */
    fun decode(key: String): Pair<ByteArray, ByteArray>? {
        val compact = key.filterNot { it.isWhitespace() }
        if (!compact.startsWith(PREFIX)) return null
        val parts = compact.removePrefix(PREFIX).split('.')
        if (parts.size != 2) return null
        return runCatching { base64.decode(parts[0]) to base64.decode(parts[1]) }.getOrNull()
    }

    private fun clean(value: String) = value.replace('\n', ' ').replace('\r', ' ').trim()
}
