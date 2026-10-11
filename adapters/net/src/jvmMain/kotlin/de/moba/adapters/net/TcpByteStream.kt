package de.moba.adapters.net

import de.moba.core.station.StationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/** TCP-Verbindung, laeuft auf Android und Windows. */
class TcpByteStream private constructor(private val socket: Socket) : ByteStream {
    private val input = socket.getInputStream()
    private val output = socket.getOutputStream()
    private val buffer = ByteArray(4096)

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        output.write(bytes)
        output.flush()
    }

    override suspend fun read(): ByteArray? = runInterruptible(Dispatchers.IO) {
        val n = try {
            input.read(buffer)
        } catch (e: IOException) {
            -1
        }
        if (n < 0) null else buffer.copyOf(n)
    }

    override fun close() = socket.close()

    companion object {
        suspend fun open(host: String, port: Int, name: String, timeoutMillis: Int = 3_000): TcpByteStream =
            withContext(Dispatchers.IO) {
                val socket = Socket()
                try {
                    socket.tcpNoDelay = true
                    socket.connect(InetSocketAddress(host, port), timeoutMillis)
                } catch (e: IOException) {
                    socket.close()
                    throw StationException(
                        "$name unter $host:$port nicht erreichbar. Stimmt die IP-Adresse, und ist die Zentrale im selben Netz?",
                    )
                }
                TcpByteStream(socket)
            }
    }
}
