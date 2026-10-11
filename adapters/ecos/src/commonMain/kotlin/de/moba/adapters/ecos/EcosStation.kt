package de.moba.adapters.ecos

import de.moba.adapters.net.ByteStream
import de.moba.adapters.net.LineSplitter
import de.moba.adapters.net.StreamStation
import de.moba.adapters.net.feedbackEvent
import de.moba.core.station.Direction
import de.moba.core.station.StationEvent
import de.moba.core.station.StationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Eine Zeile der ECoS, z. B. `1005 speed[64] dir[0]`: Objekt-ID und Werte. */
data class EcosLine(val id: Int, val values: List<Pair<String, String>>) {
    fun value(key: String) = values.firstOrNull { it.first == key }?.second

    companion object {
        private val ID = Regex("""^\s*(\d+)""")
        private val VALUE = Regex("""(\w+)\[([^\]]*)\]""")

        fun parse(line: String): EcosLine? {
            val id = ID.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            return EcosLine(id, VALUE.findAll(line).map { it.groupValues[1] to it.groupValues[2].trim() }.toList())
        }
    }
}

/** Antwort der ECoS auf einen Befehl: Fehlercode (0 = OK) und die Zeilen dazwischen. */
class EcosReply(val code: Int, val message: String, val lines: List<EcosLine>)

/**
 * Adapter fuer ESU ECoS 1 und 2 sowie Maerklin CS1 Reloaded ueber das ECoS-Textprotokoll (TCP 15471).
 *
 * Die ECoS verwaltet Loks als Objekte mit eigener ID. Faehrt die App eine Adresse, die in der ECoS
 * noch nicht angelegt ist, legt der Adapter sie mit [locoProtocol] an.
 */
class EcosStation(
    name: String,
    streamFactory: suspend () -> ByteStream,
    private val locoProtocol: String = "DCC128",
    private val accessoryProtocol: String = "DCC",
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    connectTimeoutMillis: Long = 3_000,
    private val replyTimeoutMillis: Long = 3_000,
) : StreamStation(name, streamFactory, dispatcher, connectTimeoutMillis) {
    private val splitter = LineSplitter()
    private val sendLock = Mutex()
    private val replyLock = Mutex()
    private val pendingReplies = ArrayDeque<CompletableDeferred<EcosReply>>()

    private var block: MutableList<EcosLine>? = null
    private var blockIsReply = false
    private var eventObject = 0

    private val locoLock = Mutex()
    private val locoIds = mutableMapOf<Int, Int>()
    private val locoAddresses = mutableMapOf<Int, Int>()
    private val feedbackState = mutableMapOf<Int, Int>()

    override suspend fun handshake() {
        command("get($BASE, info)")
    }

    override suspend fun afterConnect() {
        command("request($BASE, view)")
        command("get($BASE, status)")
        command("request($SWITCHES, view)")
        // Rueckmeldemodule (s88) liegen ab Objekt 100, je 16 Eingaenge.
        val size = request("get($FEEDBACK, size)").lines.firstNotNullOfOrNull { it.value("size")?.toIntOrNull() } ?: 0
        for (i in 0 until size) {
            command("request(${FEEDBACK_FIRST + i}, view)")
            command("get(${FEEDBACK_FIRST + i}, state)")
        }
    }

    override suspend fun beforeDisconnect() {
        locoIds.values.forEach { command("release($it, view, control)") }
        command("release($BASE, view)")
    }

    override fun reset() {
        locoIds.clear()
        locoAddresses.clear()
        feedbackState.clear()
        pendingReplies.forEach { it.cancel() }
        pendingReplies.clear()
        block = null
    }

    override suspend fun setTrackPower(on: Boolean) {
        command("set($BASE, ${if (on) "go" else "stop"})")
    }

    /** Die ECoS kennt keinen Lok-Nothalt bei eingeschalteter Spannung; Stop schaltet das Gleis ab. */
    override suspend fun emergencyStop() {
        command("set($BASE, stop)")
    }

    override suspend fun setLocoSpeed(address: Int, speed: Int, direction: Direction) {
        require(speed in 0..126) { "Fahrstufe muss zwischen 0 und 126 liegen" }
        val id = locoId(address)
        if (knownLoco(address)?.direction != direction) {
            command("set($id, dir[${if (direction == Direction.FORWARD) 0 else 1}])")
        }
        command("set($id, speed[${(speed * 127 + 63) / 126}])")
    }

    override suspend fun setLocoFunction(address: Int, function: Int, on: Boolean) {
        command("set(${locoId(address)}, func[$function, ${if (on) 1 else 0}])")
    }

    /** Die ECoS schaltet den Impuls selbst; r = rot (abzweigend), g = gruen (gerade). */
    override suspend fun setTurnout(address: Int, thrown: Boolean) {
        command("set($SWITCHES, switch[$accessoryProtocol$address${if (thrown) "r" else "g"}])")
    }

    /** Sucht die Lok in der ECoS oder legt sie an; meldet sich danach fuer ihre Aenderungen an. */
    private suspend fun locoId(address: Int): Int = locoLock.withLock {
        locoIds[address]?.let { return it }
        val found = request("queryObjects($LOCOS, addr)").lines
            .firstOrNull { it.value("addr")?.toIntOrNull() == address }?.id
        val id = found ?: run {
            val created = request("create($LOCOS, addr[$address], protocol[$locoProtocol], name[\"Lok $address\"], append)")
            created.lines.firstNotNullOfOrNull { it.value("id")?.toIntOrNull() }
                ?: throw StationException("$name konnte Lok $address nicht anlegen: ${created.message}")
        }
        locoIds[address] = id
        locoAddresses[id] = address
        command("request($id, view, control, force)")
        command("get($id, speed, dir)")
        id
    }

    /** Sendet einen Befehl, ohne auf die Antwort zu warten. */
    private suspend fun command(text: String) {
        enqueue(text)
    }

    /** Sendet einen Befehl und wartet auf die Antwort der ECoS. */
    private suspend fun request(text: String): EcosReply {
        val reply = withTimeoutOrNull(replyTimeoutMillis) { enqueue(text).await() }
            ?: throw StationException("$name antwortet nicht auf $text")
        if (reply.code != 0) throw StationException("$name meldet Fehler ${reply.code} (${reply.message}) bei $text")
        return reply
    }

    /** Die ECoS beantwortet Befehle in der Reihenfolge, in der sie ankommen. */
    private suspend fun enqueue(text: String): CompletableDeferred<EcosReply> = sendLock.withLock {
        val deferred = CompletableDeferred<EcosReply>()
        replyLock.withLock { pendingReplies.addLast(deferred) }
        writeLine(text)
        deferred
    }

    override suspend fun onBytes(bytes: ByteArray) {
        for (line in splitter.feed(bytes)) handleLine(line)
    }

    private suspend fun handleLine(line: String) {
        val text = line.trim()
        when {
            text.startsWith("<REPLY") -> {
                block = mutableListOf()
                blockIsReply = true
            }
            text.startsWith("<EVENT") -> {
                block = mutableListOf()
                blockIsReply = false
                eventObject = text.removePrefix("<EVENT").trim().trimEnd('>').trim().toIntOrNull() ?: 0
            }
            text.startsWith("<END") -> {
                val lines = block ?: emptyList()
                block = null
                lines.forEach { handleState(it) }
                if (blockIsReply) {
                    markReady()
                    val (code, message) = parseEnd(text)
                    replyLock.withLock { pendingReplies.removeFirstOrNull() }?.complete(EcosReply(code, message, lines))
                }
            }
            else -> EcosLine.parse(text)?.let { block?.add(it) }
        }
    }

    private fun parseEnd(text: String): Pair<Int, String> {
        val inner = text.removePrefix("<END").trimEnd('>').trim()
        val code = inner.substringBefore(' ').toIntOrNull() ?: 0
        val message = inner.substringAfter('(', "").substringBefore(')')
        return code to message
    }

    /** Wertet Zustandszeilen aus Antworten und Ereignissen gleich aus. */
    private suspend fun handleState(line: EcosLine) {
        when {
            line.id == BASE -> when (line.value("status")) {
                "GO" -> eventFlow.emit(StationEvent.TrackPowerChanged(true))
                "STOP" -> eventFlow.emit(StationEvent.TrackPowerChanged(false))
            }
            line.id == SWITCHES -> line.value("switch")?.let { value ->
                val match = SWITCH.matchEntire(value) ?: return
                val address = match.groupValues[2].toInt()
                eventFlow.emit(StationEvent.TurnoutChanged(address, thrown = match.groupValues[3] == "r"))
            }
            line.id in locoAddresses -> {
                val address = locoAddresses.getValue(line.id)
                line.value("speed")?.toIntOrNull()?.let {
                    confirmLoco(address, speed = (it.coerceIn(0, 127) * 126 + 63) / 127)
                }
                line.value("dir")?.let {
                    confirmLoco(address, direction = if (it == "0") Direction.FORWARD else Direction.REVERSE)
                }
                line.value("func")?.let { value ->
                    val parts = value.split(',').map { it.trim().toIntOrNull() }
                    val f = parts.getOrNull(0) ?: return
                    val on = parts.getOrNull(1) ?: return
                    confirmLoco(address, function = f to (on != 0))
                }
            }
            line.id in FEEDBACK_FIRST until FEEDBACK_FIRST + 100 -> line.value("state")?.let { value ->
                val bits = value.removePrefix("0x").removePrefix("0X").toIntOrNull(16) ?: return
                val module = line.id - FEEDBACK_FIRST
                val previous = feedbackState[module]
                feedbackState[module] = bits
                val changed = if (previous == null) bits else previous xor bits
                for (bit in 0 until 16) {
                    if (changed and (1 shl bit) != 0) {
                        eventFlow.emit(feedbackEvent(module * 16 + bit, occupied = bits and (1 shl bit) != 0))
                    }
                }
            }
        }
    }

    companion object {
        const val PORT = 15471
        const val BASE = 1
        const val LOCOS = 10
        const val SWITCHES = 11
        const val FEEDBACK = 26
        const val FEEDBACK_FIRST = 100

        private val SWITCH = Regex("""([A-Za-z]+)(\d+)([rg])""")
    }
}
