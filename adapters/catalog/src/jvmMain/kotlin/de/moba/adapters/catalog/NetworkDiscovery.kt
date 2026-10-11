package de.moba.adapters.catalog

import de.moba.adapters.cs.CsCodec
import de.moba.adapters.z21.Z21Codec
import de.moba.adapters.z21.Z21Message
import de.moba.core.station.FoundStation
import de.moba.core.station.StationDiscovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/**
 * Sucht Zentralen per UDP-Broadcast im lokalen Netz:
 * Z21-Zentralen beantworten die Frage nach der Seriennummer, die Maerklin CS antwortet auf einen Ping
 * per Broadcast an Port 15730. ECoS, DCC-EX und Lenz melden sich nicht; dort gibt man die IP-Adresse ein.
 */
class NetworkDiscovery(
    private val broadcast: InetAddress = InetAddress.getByName("255.255.255.255"),
    private val z21Port: Int = Z21Codec.PORT,
    private val csCommandPort: Int = CsCodec.TCP_PORT,
    private val csListenPort: Int = CsCodec.UDP_BROADCAST_PORT,
    private val timeoutMillis: Int = 1_500,
) : StationDiscovery {
    override suspend fun discover(): List<FoundStation> = coroutineScope {
        val z21 = async { findZ21() }
        val cs = async { findCs() }
        (z21.await() + cs.await()).distinctBy { it.driverId to it.host }
    }

    private suspend fun findZ21(): List<FoundStation> = runInterruptible(Dispatchers.IO) {
        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                val request = Z21Codec.getSerialNumber()
                socket.send(DatagramPacket(request, request.size, broadcast, z21Port))
                receiveAll(socket) { packet ->
                    val serial = Z21Codec.decode(packet.data.copyOf(packet.length))
                        .filterIsInstance<Z21Message.SerialNumber>().firstOrNull() ?: return@receiveAll null
                    FoundStation("z21", packet.address.hostAddress, "Z21-Zentrale, Seriennummer ${serial.serial}")
                }
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun findCs(): List<FoundStation> = runInterruptible(Dispatchers.IO) {
        runCatching {
            DatagramSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.broadcast = true
                socket.bind(InetSocketAddress(csListenPort))
                val ping = CsCodec.encode(CsCodec.ping())
                socket.send(DatagramPacket(ping, ping.size, broadcast, csCommandPort))
                receiveAll(socket) { packet ->
                    if (packet.length != CsCodec.FRAME_SIZE) return@receiveAll null
                    FoundStation("marklin-cs", packet.address.hostAddress, "Märklin Central Station")
                }
            }
        }.getOrDefault(emptyList())
    }

    /** Sammelt Antworten, bis [timeoutMillis] ohne neue Antwort vergeht. */
    private fun receiveAll(socket: DatagramSocket, parse: (DatagramPacket) -> FoundStation?): List<FoundStation> {
        socket.soTimeout = timeoutMillis
        val found = mutableListOf<FoundStation>()
        val buffer = ByteArray(1500)
        while (true) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
            } catch (e: SocketTimeoutException) {
                break
            }
            parse(packet)?.let(found::add)
        }
        return found
    }
}
