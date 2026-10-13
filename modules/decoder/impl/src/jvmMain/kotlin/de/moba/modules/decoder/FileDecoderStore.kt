package de.moba.modules.decoder

import de.moba.modules.decoder.api.DecoderCodec
import de.moba.modules.decoder.api.DecoderProfile
import de.moba.modules.decoder.api.DecoderStore
import java.io.File

/** Speichert die Decoder-Datenbank in einer Textdatei; schreibt erst in eine Zwischendatei. */
class FileDecoderStore(private val file: File) : DecoderStore {
    override fun load(): List<DecoderProfile> =
        if (file.exists()) DecoderCodec.decode(file.readText(Charsets.UTF_8)) else emptyList()

    override fun save(profiles: List<DecoderProfile>) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(DecoderCodec.encode(profiles), Charsets.UTF_8)
        if (!temp.renameTo(file)) {
            file.delete()
            check(temp.renameTo(file)) { "Decoder-Datenbank konnte nicht gespeichert werden: $file" }
        }
    }
}
