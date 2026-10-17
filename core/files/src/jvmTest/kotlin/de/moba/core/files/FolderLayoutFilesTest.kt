package de.moba.core.files

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FolderLayoutFilesTest {
    private val root: File = Files.createTempDirectory("anlagen").toFile()
    private val settings = mutableMapOf<String, String>()
    private var clock = 1_700_000_000_000L

    private fun files() = FolderLayoutFiles(root, { settings[it] }, { k, v -> settings[k] = v }, { clock })

    private fun File.put(path: String, text: String) = File(this, path).apply { parentFile.mkdirs(); writeText(text) }

    @Test
    fun layoutsAreCreatedOpenedAndRemembered() {
        root.put("gleisbilder.txt", "alt")
        val files = files()
        assertEquals(FolderLayoutFiles.DEFAULT_NAME, files.current.value.name)
        val club = files.create("Club / Anlage")
        assertEquals("Club / Anlage", club.name)
        assertTrue(File(root, "anlagen/Club _ Anlage").isDirectory)
        assertEquals(listOf(FolderLayoutFiles.DEFAULT_NAME), files.recent.value.map { it.name })
        assertEquals(listOf(FolderLayoutFiles.DEFAULT_NAME, "Club / Anlage"), files.available().map { it.name })

        // Nach einem Neustart ist die zuletzt offene Anlage wieder offen
        val again = files()
        assertEquals(club.path, again.current.value.path)
        again.open(root.path)
        assertEquals(listOf("Club / Anlage"), again.recent.value.map { it.name })
        assertFailsWith<IllegalArgumentException> { again.open(File(root, "gibt-es-nicht").path) }
        again.rename("Heimanlage")
        assertEquals("Heimanlage", files().current.value.name)
    }

    @Test
    fun resourcesListDataImagesAndSoundsButNoBackups() {
        val files = files()
        root.put("gleisbilder.txt", "x")
        root.put("fahrzeugbilder/br218.jpg", "jpg")
        root.put("klaenge/pfiff.wav", "wav")
        root.put("fahrzeuge.txt.tmp", "halb")
        files.backup()
        files.create("Zweite")
        files.open(root.path)
        val res = files.resources().associateBy { it.path }
        assertEquals(setOf("gleisbilder.txt", "fahrzeugbilder/br218.jpg", "klaenge/pfiff.wav"), res.keys)
        assertEquals(ResourceKind.IMAGE, res["fahrzeugbilder/br218.jpg"]?.kind)
        assertEquals(DataPart.SOUNDS, res["klaenge/pfiff.wav"]?.part)
        assertEquals("wav", files.read("klaenge/pfiff.wav")?.decodeToString())
        assertFailsWith<IllegalArgumentException> { files.read("../geheim.txt") }
        files.delete("klaenge/pfiff.wav")
        assertFalse(File(root, "klaenge/pfiff.wav").exists())
    }

    @Test
    fun backupIsRestoredAndAutomaticBackupsAreLimited() {
        val files = files()
        assertNull(files.autoBackup())
        root.put("gleisbilder.txt", "eins")
        root.put("klaenge/a.wav", "a")
        val first = files.backup()
        root.put("gleisbilder.txt", "zwei")
        root.put("klaenge/b.wav", "b")
        clock += 1000
        files.restore(first.name)
        assertEquals("eins", File(root, "gleisbilder.txt").readText())
        assertFalse(File(root, "klaenge/b.wav").exists())
        // Vor dem Wiederherstellen wurde der alte Stand gesichert
        assertTrue(files.backups().any { it.name.contains("vor-wiederherstellen") })

        assertNotNull(files.autoBackup(minIntervalMillis = 10_000, keep = 2))
        clock += 5_000
        assertNull(files.autoBackup(minIntervalMillis = 10_000, keep = 2))
        repeat(3) {
            clock += 20_000
            assertNotNull(files.autoBackup(minIntervalMillis = 10_000, keep = 2))
        }
        assertEquals(2, files.backups().count { it.name.contains("automatisch") })
    }

    @Test
    fun selectedPartsAreExportedAndImportedIntoAnotherLayout() {
        val files = files()
        root.put("gleisbilder.txt", "plan")
        root.put("fahrzeuge.txt", "loks")
        root.put("fahrzeugbilder/l1.png", "foto")
        val zip = files.export(setOf(DataPart.VEHICLES))
        assertEquals(setOf(DataPart.VEHICLES), files.inspect(zip))

        files.create("Club")
        files.dir.put("fahrzeuge.txt", "andere")
        files.dir.put("fahrzeugbilder/alt.png", "alt")
        files.import(zip, setOf(DataPart.VEHICLES, DataPart.TRACK_PLANS))
        assertEquals("loks", File(files.dir, "fahrzeuge.txt").readText())
        assertTrue(File(files.dir, "fahrzeugbilder/l1.png").isFile)
        assertFalse(File(files.dir, "fahrzeugbilder/alt.png").exists())
        assertFalse(File(files.dir, "gleisbilder.txt").exists())
        assertTrue(files.backups().any { it.name.contains("vor-import") })
        assertEquals(setOf(DataPart.VEHICLES), files.parts())

        assertFailsWith<IllegalArgumentException> { files.inspect("kein zip".toByteArray()) }
    }
}
