package de.moba.modules.vehicles

import de.moba.modules.vehicles.api.Fleet
import de.moba.modules.vehicles.api.VehicleCodec
import de.moba.modules.vehicles.api.VehicleStore
import java.io.File

/** Speichert alle Fahrzeuge in einer Textdatei; schreibt erst in eine Zwischendatei, damit nichts halb gespeichert wird. */
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
}
