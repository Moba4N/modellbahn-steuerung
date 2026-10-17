package de.moba.modules.vehicles

import de.moba.modules.vehicles.api.Fleet
import de.moba.modules.vehicles.api.VehicleCodec
import de.moba.modules.vehicles.api.VehicleStore
import java.io.File

/**
 * Speichert alle Fahrzeuge in einer Textdatei; schreibt erst in eine Zwischendatei, damit nichts halb gespeichert wird.
 * Fotos liegen daneben im Ordner "fahrzeugbilder", eine Datei je Fahrzeug.
 */
class FileVehicleStore(private val file: File) : VehicleStore {
    override fun load(): Fleet? = if (file.exists()) VehicleCodec.decode(file.readText(Charsets.UTF_8)) else null

    override fun save(fleet: Fleet) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(VehicleCodec.encode(fleet), Charsets.UTF_8)
        if (!temp.renameTo(file)) {
            file.delete()
            check(temp.renameTo(file)) { "Fahrzeuge konnten nicht gespeichert werden: $file" }
        }
    }

    private val imageDir = File(file.parentFile, "fahrzeugbilder")

    private fun imageFile(id: String) = File(imageDir, id.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".bild")

    override fun loadImage(id: String): ByteArray? = imageFile(id).takeIf { it.exists() }?.readBytes()

    override fun saveImage(id: String, bytes: ByteArray?) {
        val target = imageFile(id)
        if (bytes == null) {
            target.delete()
            return
        }
        imageDir.mkdirs()
        val temp = File(imageDir, target.name + ".tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(target)) {
            target.delete()
            check(temp.renameTo(target)) { "Bild konnte nicht gespeichert werden: $target" }
        }
    }
}
