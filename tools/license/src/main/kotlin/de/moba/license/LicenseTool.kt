package de.moba.license

import de.moba.core.runtime.EcLicenseVerifier
import de.moba.core.runtime.License
import de.moba.core.runtime.LicenseCheck
import de.moba.core.runtime.LicenseSigner
import de.moba.core.runtime.ManufacturerKey
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

private const val USAGE = """
Lizenzwerkzeug der Modellbahn-Steuerung

  schluessel --ordner <Ordner>
      Erzeugt ein neues Schluesselpaar. privat.key geheim halten (nie ins Git!),
      den oeffentlichen Schluessel in ManufacturerKey.kt eintragen.

  erstellen --privat <privat.key> --name "<Kunde>" --module pro,decoder [--bis JJJJ-MM-TT] [--id <Nummer>]
      Erstellt einen Lizenzschluessel fuer einen Kunden. Module: basis, pro, club, decoder.

  pruefen <Lizenzschluessel>
      Prueft einen Schluessel mit dem eingebauten oeffentlichen Schluessel.

Aufruf ueber Gradle: ./gradlew :tools:license:run --args="erstellen --privat ..."
"""

private val KNOWN_FEATURES = setOf("basis", "pro", "club", "decoder")

fun main(args: Array<String>) {
    val command = args.firstOrNull() ?: fail(USAGE)
    val options = parseOptions(args.drop(1))
    when (command) {
        "schluessel" -> createKeyPair(File(options["ordner"] ?: fail("--ordner fehlt")))
        "erstellen" -> createLicense(options)
        "pruefen" -> check(args.drop(1).joinToString(""))
        else -> fail(USAGE)
    }
}

private fun createKeyPair(dir: File) {
    val privateFile = File(dir, "privat.key")
    if (privateFile.exists()) fail("$privateFile gibt es schon. Ein neues Paar macht alle bisherigen Lizenzen ungueltig.")
    dir.mkdirs()
    val (private, public) = LicenseSigner.generateKeyPair()
    privateFile.writeText(private + "\n")
    File(dir, "oeffentlich.key").writeText(public + "\n")
    println("Privater Schluessel: $privateFile (geheim halten, sicher aufbewahren)")
    println("Oeffentlicher Schluessel fuer ManufacturerKey.kt:")
    println(public)
}

private fun createLicense(options: Map<String, String>) {
    val privateFile = File(options["privat"] ?: fail("--privat fehlt"))
    val name = options["name"] ?: fail("--name fehlt")
    val features = (options["module"] ?: fail("--module fehlt")).split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
    val unknown = features - KNOWN_FEATURES
    if (features.isEmpty() || unknown.isNotEmpty()) fail("Unbekannte Module: $unknown. Erlaubt: $KNOWN_FEATURES")
    val expires = options["bis"].orEmpty()
    if (expires.isNotEmpty()) runCatching { LocalDate.parse(expires) }.getOrElse { fail("--bis bitte als JJJJ-MM-TT") }
    val today = LocalDate.now()
    val id = options["id"] ?: "L-" + today.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + (1000..9999).random()
    val license = License(id, name, features, today.toString(), expires)
    println(LicenseSigner(privateFile.readText()).sign(license))
}

private fun check(key: String) {
    when (val result = EcLicenseVerifier(ManufacturerKey.PUBLIC).check(key, LocalDate.now().toString())) {
        is LicenseCheck.Valid -> println("Gueltig: ${result.license}")
        is LicenseCheck.Invalid -> fail(result.reason)
    }
}

private fun parseOptions(args: List<String>): Map<String, String> {
    val result = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val arg = args[i]
        if (arg.startsWith("--") && i + 1 < args.size) {
            result[arg.removePrefix("--")] = args[i + 1]
            i += 2
        } else {
            i++
        }
    }
    return result
}

private fun fail(message: String): Nothing {
    System.err.println(message.trim())
    exitProcess(1)
}
