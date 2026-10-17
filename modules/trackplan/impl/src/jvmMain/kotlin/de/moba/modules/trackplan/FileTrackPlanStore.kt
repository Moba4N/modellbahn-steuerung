package de.moba.modules.trackplan

import de.moba.modules.trackplan.api.LayoutObjectCodec
import de.moba.modules.trackplan.api.LayoutObjectStore
import de.moba.modules.trackplan.api.LayoutObjects
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

/** Speichert Bahnhoefe und Booster in einer eigenen Textdatei neben den Gleisbildern. */
class FileLayoutObjectStore(private val file: File) : LayoutObjectStore {
    override fun load(): LayoutObjects? = if (file.exists()) LayoutObjectCodec.decode(file.readText(Charsets.UTF_8)) else null

    override fun save(objects: LayoutObjects) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(LayoutObjectCodec.encode(objects), Charsets.UTF_8)
        if (!temp.renameTo(file)) {
            file.delete()
            check(temp.renameTo(file)) { "Bahnhöfe und Booster konnten nicht gespeichert werden: $file" }
        }
    }
}
