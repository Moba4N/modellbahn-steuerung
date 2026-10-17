package de.moba.modules.throttles

import de.moba.modules.throttles.api.ThrottleBarStore
import java.io.File

/** Speichert die offenen Fahrregler als Adressliste, eine Zeile. */
class FileThrottleBarStore(private val file: File) : ThrottleBarStore {
    override fun load(): List<Int> =
        if (file.exists()) file.readText().split(',', ' ', '\n').mapNotNull { it.trim().toIntOrNull() } else emptyList()

    override fun save(addresses: List<Int>) {
        file.parentFile?.mkdirs()
        file.writeText(addresses.joinToString(","))
    }
}
