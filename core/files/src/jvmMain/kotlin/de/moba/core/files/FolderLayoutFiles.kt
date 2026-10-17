package de.moba.core.files

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Anlagen als Ordner: die Standard-Anlage ist [root] selbst (dort lagen die Daten schon immer),
 * weitere liegen unter [root]/anlagen oder an beliebiger Stelle (Windows: "Ordner öffnen").
 * Welche Anlage offen ist und die zuletzt verwendeten merkt sich [settingGet]/[settingSet].
 */
class FolderLayoutFiles(
    private val root: File,
    private val settingGet: (String) -> String?,
    private val settingSet: (String, String) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) : LayoutFiles {
    private val _current = MutableStateFlow(info(startDir()))
    override val current: StateFlow<LayoutInfo> = _current

    private val _recent = MutableStateFlow(loadRecent())
    override val recent: StateFlow<List<LayoutInfo>> = _recent

    /** Ordner der offenen Anlage; dort lesen und schreiben die Module ihre Dateien. */
    val dir: File get() = File(_current.value.path)

    private fun startDir(): File =
        settingGet(KEY_CURRENT)?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isDirectory } ?: root

    private fun loadRecent(): List<LayoutInfo> = settingGet(KEY_RECENT).orEmpty().lines()
        .filter { it.isNotBlank() && it != _current.value.path }
        .map(::File).filter { it.isDirectory }.map(::info).distinctBy { it.path }.take(LayoutFiles.MAX_RECENT)

    private fun info(dir: File): LayoutInfo {
        val name = File(dir, INFO_FILE).takeIf { it.isFile }?.readLines(Charsets.UTF_8)
            ?.firstNotNullOfOrNull { line -> line.removePrefix("name=").takeIf { line.startsWith("name=") && it.isNotBlank() } }
        return LayoutInfo(dir.absoluteFile.normalize().path, name ?: if (same(dir, root)) DEFAULT_NAME else dir.name)
    }

    private fun same(a: File, b: File) = a.absoluteFile.normalize() == b.absoluteFile.normalize()

    override fun available(): List<LayoutInfo> {
        val others = File(root, LAYOUTS).listFiles()?.filter { it.isDirectory }?.sortedBy { it.name.lowercase() }.orEmpty()
        return (listOf(root) + others).map(::info)
    }

    override fun open(path: String) {
        val target = File(path)
        require(target.isDirectory) { "Den Ordner $path gibt es nicht." }
        val previous = _current.value
        val next = info(target)
        if (next.path == previous.path) return
        _current.value = next
        settingSet(KEY_CURRENT, next.path)
        _recent.value = (listOf(previous) + _recent.value).filter { it.path != next.path }.distinctBy { it.path }.take(LayoutFiles.MAX_RECENT)
        settingSet(KEY_RECENT, _recent.value.joinToString("\n") { it.path })
    }

    override fun create(name: String): LayoutInfo {
        val clean = name.trim().ifEmpty { "Neue Anlage" }.take(60)
        val base = clean.map { if (it.isLetterOrDigit() || it in " -_") it else '_' }.joinToString("").trim().ifEmpty { "anlage" }
        val parent = File(root, LAYOUTS)
        val dir = generateSequence(1) { it + 1 }.map { n -> File(parent, if (n == 1) base else "$base $n") }.first { !it.exists() }
        dir.mkdirs()
        writeName(dir, clean)
        open(dir.path)
        return _current.value
    }

    override fun rename(name: String) {
        val clean = name.trim().take(60)
        require(clean.isNotEmpty()) { "Der Name darf nicht leer sein." }
        writeName(dir, clean)
        _current.value = info(dir)
    }

    private fun writeName(dir: File, name: String) {
        dir.mkdirs()
        File(dir, INFO_FILE).writeText("name=$name\n", Charsets.UTF_8)
    }

    // ------------------------------------------------------------ Dateien

    /** Alle Dateien der Anlage, ohne Sicherungen, Zwischendateien und andere Anlagen. */
    private fun files(): List<Pair<String, File>> {
        val base = dir
        if (!base.isDirectory) return emptyList()
        return base.walkTopDown()
            .onEnter { d -> same(d, base) || d.name != BACKUPS && !(same(base, root) && same(d, File(root, LAYOUTS))) }
            .filter { it.isFile && !it.name.endsWith(".tmp") }
            .map { it.relativeTo(base).invariantSeparatorsPath to it }
            .sortedBy { it.first.lowercase() }
            .toList()
    }

    override fun resources(): List<ResourceFile> = files().map { (path, file) ->
        val ext = path.substringAfterLast('.', "").lowercase()
        val kind = when {
            path.startsWith("klaenge/") || ext in SOUND_EXT -> ResourceKind.SOUND
            path.startsWith("fahrzeugbilder/") || ext in IMAGE_EXT || path == "hintergrund.bild" -> ResourceKind.IMAGE
            ext == "txt" -> ResourceKind.DATA
            else -> ResourceKind.OTHER
        }
        ResourceFile(path, file.length(), file.lastModified(), kind, DataPart.of(path))
    }

    private fun resolve(base: File, path: String): File {
        val file = File(base, path).absoluteFile.normalize()
        require(file.path.startsWith(base.absoluteFile.normalize().path + File.separator)) { "Ungültiger Pfad: $path" }
        return file
    }

    override fun read(path: String): ByteArray? = resolve(dir, path).takeIf { it.isFile }?.readBytes()

    override fun delete(path: String) {
        resolve(dir, path).delete()
    }

    override fun parts(): Set<DataPart> = files().mapNotNull { DataPart.of(it.first) }.toSet()

    // ------------------------------------------------------------ Sicherungen

    private val backupDir get() = File(dir, BACKUPS)

    override fun backups(): List<BackupInfo> = backupDir.listFiles()?.filter { it.isFile && it.name.endsWith(".zip") }
        ?.sortedByDescending { it.name }?.map { BackupInfo(it.name, it.length(), it.lastModified()) }.orEmpty()

    override fun backup(label: String): BackupInfo {
        val stamp = STAMP.format(Instant.ofEpochMilli(now()).atZone(ZoneId.systemDefault()))
        val suffix = if (label.isBlank()) "" else "-" + label.filter { it.isLetterOrDigit() || it == '-' }
        backupDir.mkdirs()
        val file = generateSequence(1) { it + 1 }
            .map { n -> File(backupDir, "sicherung-$stamp$suffix${if (n == 1) "" else "-$n"}.zip") }.first { !it.exists() }
        file.writeBytes(zip(DataPart.entries.toSet()))
        file.setLastModified(now())
        return BackupInfo(file.name, file.length(), file.lastModified())
    }

    override fun restore(name: String) {
        val bytes = resolve(backupDir, name).takeIf { it.isFile }?.readBytes() ?: throw IllegalArgumentException("Sicherung $name nicht gefunden.")
        val parts = inspect(bytes)
        if (parts().isNotEmpty()) backup("vor-wiederherstellen")
        write(bytes, parts)
    }

    override fun deleteBackup(name: String) {
        resolve(backupDir, name).delete()
    }

    override fun autoBackup(minIntervalMillis: Long, keep: Int): BackupInfo? {
        if (parts().isEmpty()) return null
        val auto = backups().filter { it.name.contains("-$AUTO") }
        val last = auto.maxOfOrNull { it.modifiedMillis }
        if (last != null && now() - last < minIntervalMillis) return null
        val made = backup(AUTO)
        (listOf(made) + auto).sortedByDescending { it.name }.drop(keep).forEach { deleteBackup(it.name) }
        return made
    }

    // ------------------------------------------------------------ Import und Export

    override fun export(parts: Set<DataPart>): ByteArray = zip(parts)

    private fun zip(parts: Set<DataPart>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry(MARKER))
            z.write("version=1\nname=${_current.value.name}\n".toByteArray(Charsets.UTF_8))
            z.closeEntry()
            File(dir, INFO_FILE).takeIf { it.isFile }?.let { f ->
                z.putNextEntry(ZipEntry(INFO_FILE))
                z.write(f.readBytes())
                z.closeEntry()
            }
            for ((path, file) in files()) {
                val part = DataPart.of(path) ?: continue
                if (part !in parts) continue
                z.putNextEntry(ZipEntry(path).apply { time = file.lastModified() })
                file.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun entries(zip: ByteArray): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        runCatching {
            ZipInputStream(ByteArrayInputStream(zip)).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (!e.isDirectory) result[e.name.replace('\\', '/')] = z.readBytes()
                }
            }
        }.onFailure { throw IllegalArgumentException("Die Datei ist kein ZIP-Archiv.") }
        return result
    }

    override fun inspect(zip: ByteArray): Set<DataPart> {
        val names = entries(zip).keys
        val parts = names.mapNotNull(DataPart::of).toSet()
        require(MARKER in names || parts.isNotEmpty()) { "Das Archiv enthält keine Daten dieser App." }
        require(parts.isNotEmpty()) { "Das Archiv enthält keine Anlagendaten." }
        return parts
    }

    override fun import(zip: ByteArray, parts: Set<DataPart>) {
        val available = inspect(zip)
        if (parts().isNotEmpty()) backup("vor-import")
        write(zip, parts intersect available)
    }

    /** Ersetzt die Teile [parts] im Anlagen-Ordner durch die Eintraege aus [zip]. */
    private fun write(zip: ByteArray, parts: Set<DataPart>) {
        val base = dir
        val entries = entries(zip)
        for (part in parts) {
            part.files.forEach { File(base, it).delete() }
            part.folders.forEach { File(base, it).deleteRecursively() }
        }
        for ((path, bytes) in entries) {
            val part = DataPart.of(path) ?: continue
            if (part !in parts) continue
            val target = resolve(base, path)
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
        }
    }

    companion object {
        const val DEFAULT_NAME = "Meine Anlage"
        const val INFO_FILE = "anlage.txt"
        const val LAYOUTS = "anlagen"
        const val BACKUPS = "sicherungen"
        const val MARKER = "modellbahn-steuerung.txt"
        private const val AUTO = "automatisch"
        private const val KEY_CURRENT = "datei.anlage"
        private const val KEY_RECENT = "datei.zuletzt"
        private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
        private val SOUND_EXT = setOf("wav", "mp3", "ogg", "aiff", "aif", "au", "m4a")
        private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp", "bild")
    }
}
