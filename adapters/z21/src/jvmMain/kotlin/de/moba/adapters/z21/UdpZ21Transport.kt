package de.moba.adapters.z21

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

/** UDP-Verbindung zur Zentrale, laeuft auf Android und Windows. */
class UdpZ21Transport(
    host: String,
    port: Int = Z21Codec.PORT,
) : Z21Transport {
    private val target = InetSocketAddress(host, port)
    private val socket = DatagramSocket()
    private val buffer = ByteArray(1500)

    override suspend fun send(packet: ByteArray) = withContext(Dispatchers.IO) {
        socket.send(DatagramPacket(packet, packet.size, target))
    }

    override suspend fun receive(): ByteArray = runInterruptible(Dispatchers.IO) {
        val packet = DatagramPacket(buffer, buffer.size)
        socket.receive(packet)
        packet.data.copyOf(packet.length)
    }

    override fun close() = socket.close()
}

/** Z21-Zentrale unter [host] per UDP, z. B. `z21Station("192.168.0.111", "YaMoRC YD7001")`. */
fun z21Station(host: String, name: String = "Z21-Zentrale ($host)") =
    Z21Station(name = name, transportFactory = { UdpZ21Transport(host) })
