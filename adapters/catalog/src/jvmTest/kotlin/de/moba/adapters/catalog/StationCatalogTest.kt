package de.moba.adapters.catalog

import de.moba.adapters.z21.Z21Codec
import de.moba.core.station.FoundStation
import kotlinx.coroutines.runBlocking
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StationCatalogTest {
    @Test
    fun driverIdsAreUniqueAndSimulatorComesFirst() {
        val ids = StationCatalog.drivers.map { it.id }
        assertEquals(ids.toSet().size, ids.size)
        assertEquals("simulator", ids.first())
        assertTrue(StationCatalog.drivers.all { it.options.all { o -> o.choices.isNotEmpty() } })
    }

    @Test
    fun findsZ21AnsweringOnLocalhost() = runBlocking {
        val loopback = InetAddress.getLoopbackAddress()
        val fakeZ21 = DatagramSocket(0, loopback)
        val unusedCs = DatagramSocket(0, loopback)
        val csPort = unusedCs.localPort
        unusedCs.close()
        val responder = thread {
            val buffer = ByteArray(64)
            val packet = DatagramPacket(buffer, buffer.size)
            fakeZ21.receive(packet)
            val reply = Z21Codec.packet(Z21Codec.HEADER_GET_SERIAL, byteArrayOf(0x39, 0x30, 0, 0))
            fakeZ21.send(DatagramPacket(reply, reply.size, packet.socketAddress))
        }

        val found = NetworkDiscovery(
            broadcast = loopback,
            z21Port = fakeZ21.localPort,
            csCommandPort = csPort,
            csListenPort = 0,
            timeoutMillis = 500,
        ).discover()

        responder.join()
        fakeZ21.close()
        assertEquals(listOf(FoundStation("z21", loopback.hostAddress, "Z21-Zentrale, Seriennummer 12345")), found)
    }
}
