package de.moba.modules.actions

import de.moba.modules.actions.api.ActionBook
import de.moba.modules.actions.api.ActionCodec
import de.moba.modules.actions.api.ActionStore
import java.io.File
import java.time.LocalTime

actual fun localMinutes(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

/** Speichert Aktionen und Brennstoffvorraete in einer Textdatei; schreibt erst in eine Zwischendatei. */
class FileActionStore(private val file: File) : ActionStore {
    override fun load(): ActionBook? = if (file.exists()) ActionCodec.decode(file.readText(Charsets.UTF_8)) else null

    override fun save(book: ActionBook) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(ActionCodec.encode(book), Charsets.UTF_8)
        if (!temp.renameTo(file)) {
            file.delete()
            check(temp.renameTo(file)) { "Aktionen konnten nicht gespeichert werden: $file" }
        }
    }
}
