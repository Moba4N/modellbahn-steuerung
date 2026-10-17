package de.moba.modules.routes

import de.moba.modules.routes.api.RouteBook
import de.moba.modules.routes.api.RouteCodec
import de.moba.modules.routes.api.RouteStore
import java.io.File

/** Speichert Fahrstrassen, Pendelstrecken und Zugrouten in einer Textdatei; schreibt erst in eine Zwischendatei. */
class FileRouteStore(private val file: File) : RouteStore {
    override fun load(): RouteBook? = if (file.exists()) RouteCodec.decode(file.readText(Charsets.UTF_8)) else null

    override fun save(book: RouteBook) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(RouteCodec.encode(book), Charsets.UTF_8)
        if (!temp.renameTo(file)) {
            file.delete()
            check(temp.renameTo(file)) { "Fahrstraßen konnten nicht gespeichert werden: $file" }
        }
    }
}
