package de.moba.core.files

import kotlinx.coroutines.flow.StateFlow

/** Eine Anlage: ein Ordner mit allen Daten (Gleisbilder, Fahrzeuge, Fahrstrassen ...). [path] ist der volle Pfad. */
data class LayoutInfo(val path: String, val name: String)

/** Teil der Anlagendaten, der sich einzeln exportieren und importieren laesst. */
enum class DataPart(val label: String, val files: List<String>, val folders: List<String> = emptyList()) {
    TRACK_PLANS("Gleisbilder", listOf("gleisbilder.txt")),
    VEHICLES("Fahrzeuge mit Fotos", listOf("fahrzeuge.txt"), listOf("fahrzeugbilder")),
    ROUTES("Fahrstraßen, Pendel und Zugrouten", listOf("fahrstrassen.txt")),
    ACTIONS("Aktionen und Brennstoff", listOf("aktionen.txt")),
    SYMBOLS("Eigene Symbole", listOf("symbole.txt")),
    DECODERS("Decoder", listOf("decoder.txt")),
    CALIBRATION("Einmessungen", listOf("einmessen.txt")),
    THROTTLES("Fahrregler-Leiste", listOf("fahrregler.txt")),
    SOUNDS("Klänge", emptyList(), listOf("klaenge")),
    ;

    /** Gehoert die Datei (Pfad relativ zum Anlagen-Ordner, mit "/") zu diesem Teil? */
    fun owns(relative: String): Boolean = relative in files || folders.any { relative.startsWith("$it/") }

    companion object {
        fun of(relative: String): DataPart? = entries.firstOrNull { it.owns(relative) }
    }
}

/** Art einer Datei im Ressourcen-Browser. */
enum class ResourceKind(val label: String) { DATA("Daten"), IMAGE("Bild"), SOUND("Klang"), OTHER("Sonstiges") }

/** Eine Datei der Anlage; [path] relativ zum Anlagen-Ordner, mit "/". */
data class ResourceFile(val path: String, val size: Long, val modifiedMillis: Long, val kind: ResourceKind, val part: DataPart?) {
    val name: String get() = path.substringAfterLast('/')
}

/** Eine Sicherung im Ordner "sicherungen" der Anlage. */
data class BackupInfo(val name: String, val size: Long, val modifiedMillis: Long)

/**
 * Datei-Handhabung wie in iTrain: mehrere Anlagen (je ein Ordner), zuletzt verwendete, Sicherungen,
 * Import und Export als ZIP und ein Blick auf alle Dateien der Anlage.
 * Was die laufenden Module geladen haben, aendert sich erst nach [onReload] (die App startet die Module neu).
 */
interface LayoutFiles {
    val current: StateFlow<LayoutInfo>

    /** Zuletzt geoeffnete Anlagen, neueste zuerst, ohne die aktuelle; hoechstens [MAX_RECENT]. */
    val recent: StateFlow<List<LayoutInfo>>

    /** Alle Anlagen im App-Ordner (die Standard-Anlage und alle unter "anlagen"). */
    fun available(): List<LayoutInfo>

    /** Oeffnet die Anlage im Ordner [path]. @throws IllegalArgumentException, wenn es den Ordner nicht gibt */
    fun open(path: String)

    /** Legt eine neue, leere Anlage an und oeffnet sie. */
    fun create(name: String): LayoutInfo

    fun rename(name: String)

    fun resources(): List<ResourceFile>
    fun read(path: String): ByteArray?
    fun delete(path: String)

    fun backups(): List<BackupInfo>

    /** Sichert alle Daten der Anlage als ZIP in "sicherungen". [label] haengt am Dateinamen, z. B. "automatisch". */
    fun backup(label: String = ""): BackupInfo

    /** Spielt eine Sicherung zurueck; vorher wird der jetzige Stand gesichert ("vor-wiederherstellen"). */
    fun restore(name: String)

    fun deleteBackup(name: String)

    /** Sichert hoechstens einmal je [minIntervalMillis] automatisch und behaelt die neuesten [keep] automatischen. */
    fun autoBackup(minIntervalMillis: Long = DAY, keep: Int = 10): BackupInfo?

    /** Welche Teile die Anlage hat. */
    fun parts(): Set<DataPart>

    /** Die gewaehlten Teile als ZIP. */
    fun export(parts: Set<DataPart>): ByteArray

    /** Welche Teile ein ZIP enthaelt. @throws IllegalArgumentException, wenn es kein Export dieser App ist */
    fun inspect(zip: ByteArray): Set<DataPart>

    /** Ersetzt die gewaehlten Teile durch die aus dem ZIP; vorher wird gesichert ("vor-import"). */
    fun import(zip: ByteArray, parts: Set<DataPart>)

    companion object {
        const val MAX_RECENT = 6
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
