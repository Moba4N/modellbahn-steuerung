package de.moba.modules.trackplan

import de.moba.modules.trackplan.api.TrackPlan
import de.moba.modules.trackplan.api.TrackPlanCodec
import de.moba.modules.trackplan.api.TrackPlanStore
import java.io.File

/** Speichert alle Gleisbilder in einer Textdatei; schreibt erst in eine Zwischendatei, damit nichts halb gespeichert wird. */
class FileTrackPlanStore(private val file: File) : TrackPlanStore {
    override fun load(): List<TrackPlan> =
        if (file.exists()) TrackPlanCodec.decode(file.readText(Charsets.UTF_8)) else emptyList()

    override fun save(plans: List<TrackPlan>) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(TrackPlanCodec.encode(plans), Charsets.UTF_8)
        if (!temp.renameTo(file)) {
            file.delete()
            check(temp.renameTo(file)) { "Gleisbilder konnten nicht gespeichert werden: $file" }
        }
    }
}
