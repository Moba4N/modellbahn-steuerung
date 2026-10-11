package de.moba.adapters.net

import kotlinx.coroutines.channels.Channel

/** Eine Byte-Verbindung zur Zentrale, z. B. TCP. Auf Android und Windows: [TcpByteStream]. */
interface ByteStream {
    suspend fun write(bytes: ByteArray)

    /** Wartet auf Daten; liefert null, wenn die Gegenseite die Verbindung geschlossen hat. */
    suspend fun read(): ByteArray?
    fun close()
}

/** Zwei verbundene Enden im Speicher, fuer Tests mit nachgebauten Zentralen. */
class MemoryByteStream private constructor(
    private val incoming: Channel<ByteArray>,
    private val outgoing: Channel<ByteArray>,
) : ByteStream {
    override suspend fun write(bytes: ByteArray) = outgoing.send(bytes)
    override suspend fun read(): ByteArray? = incoming.receiveCatching().getOrNull()
    override fun close() {
        incoming.close()
        outgoing.close()
    }

    companion object {
        /** Liefert (App-Seite, Zentralen-Seite). */
        fun pair(): Pair<MemoryByteStream, MemoryByteStream> {
            val a = Channel<ByteArray>(Channel.UNLIMITED)
            val b = Channel<ByteArray>(Channel.UNLIMITED)
            return MemoryByteStream(a, b) to MemoryByteStream(b, a)
        }
    }
}

/** Zerlegt einen Byte-Strom in Textzeilen (fuer ECoS und DCC-EX). */
class LineSplitter {
    private val pending = StringBuilder()

    fun feed(bytes: ByteArray): List<String> {
        pending.append(bytes.decodeToString())
        val lines = mutableListOf<String>()
        while (true) {
            val end = pending.indexOf('\n')
            if (end < 0) break
            lines += pending.substring(0, end).trimEnd('\r')
            pending.deleteRange(0, end + 1)
        }
        return lines
    }
}
