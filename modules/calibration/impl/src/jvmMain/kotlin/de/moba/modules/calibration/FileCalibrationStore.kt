package de.moba.modules.calibration

import de.moba.modules.calibration.api.CalibrationCodec
import de.moba.modules.calibration.api.CalibrationStore
import de.moba.modules.calibration.api.SpeedProfile
import java.io.File

/** Speichert die Messergebnisse in einer Textdatei; schreibt erst in eine Zwischendatei. */
class FileCalibrationStore(private val file: File) : CalibrationStore {
    override fun load(): List<SpeedProfile> =
        if (file.exists()) CalibrationCodec.decode(file.readText(Charsets.UTF_8)) else emptyList()

    override fun save(profiles: List<SpeedProfile>) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(CalibrationCodec.encode(profiles), Charsets.UTF_8)
        if (!temp.renameTo(file)) {
            file.delete()
            check(temp.renameTo(file)) { "Messergebnisse konnten nicht gespeichert werden: $file" }
        }
    }
}
