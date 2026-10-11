package de.moba.adapters.z21

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

class UdpZ21TransportTest {
    /** Eine Mini-Zentrale auf localhost, die auf die Seriennummer-Abfrage antwortet. */
    @Test
    fun talksToZ21OverUdp() = runBlocking {
        val fakeCentral = DatagramSocket(0, InetAddress.getLoopbackAddress())
        val server = thread {
            val buf = ByteArray(64)
            repeat(3) {
                val p = DatagramPacket(buf, buf.size)
                fakeCentral.receive(p)
                if (p.data.copyOf(p.length).contentEquals(Z21Codec.getSerialNumber())) {
                    val answer = Z21Codec.packet(Z21Codec.HEADER_GET_SERIAL, byteArrayOf(0x39, 0x30, 0, 0))
                    fakeCentral.send(DatagramPacket(answer, answer.size, p.socketAddress))
                }
            }
        }

        val station = Z21Station(
            name = "Test",
            transportFactory = { UdpZ21Transport("127.0.0.1", fakeCentral.localPort) },
        )
        withTimeout(5_000) { station.connect() }
        station.disconnect()
        server.join(2_000)
        fakeCentral.close()
        assertEquals(false, server.isAlive)
    }
}
