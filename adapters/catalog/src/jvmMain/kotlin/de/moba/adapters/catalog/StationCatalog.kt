package de.moba.adapters.catalog

import de.moba.adapters.cs.CsCodec
import de.moba.adapters.cs.CsProtocol
import de.moba.adapters.cs.CsStation
import de.moba.adapters.dccex.DccExStation
import de.moba.adapters.ecos.EcosStation
import de.moba.adapters.net.TcpByteStream
import de.moba.adapters.simulator.SimulatorStation
import de.moba.adapters.xpressnet.XpressNetCodec
import de.moba.adapters.xpressnet.XpressNetStation
import de.moba.adapters.z21.Z21Codec
import de.moba.adapters.z21.z21Station
import de.moba.core.station.CommandStation
import de.moba.core.station.DriverOption
import de.moba.core.station.StationConfig
import de.moba.core.station.StationDiscovery
import de.moba.core.station.StationDriver

/** Alle Zentralen, die die App kennt. Alle Treiber sind kostenlos. */
object StationCatalog {
    val simulator: StationDriver = object : StationDriver {
        override val id = "simulator"
        override val title = "Simulator"
        override val devices = "Ohne Hardware ausprobieren"
        override val models = listOf(
            "Simulator (ohne Hardware)",
        )
        override val needsHost = false
        override fun create(config: StationConfig): CommandStation = SimulatorStation()
    }

    val z21: StationDriver = object : StationDriver {
        override val id = "z21"
        override val title = "Z21-Protokoll"
        override val devices = "YaMoRC YD7001, Roco/Fleischmann Z21 und z21, Digikeijs DR5000"
        override val models = listOf(
            "YaMoRC YD7001",
            "Roco Z21",
            "Roco z21 / z21start",
            "Digikeijs DR5000",
            "Andere Zentrale mit Z21-Protokoll",
        )
        override val defaultPort = Z21Codec.PORT
        override fun create(config: StationConfig) =
            z21Station(config.host, name = "${config.model ?: "Z21-Zentrale"} (${config.host})", port = config.port ?: Z21Codec.PORT)
    }

    val marklinCs: StationDriver = object : StationDriver {
        override val id = "marklin-cs"
        override val title = "Märklin Central Station"
        override val devices = "Central Station 2, 3 und 3plus"
        override val models = listOf(
            "Märklin Central Station 2",
            "Märklin Central Station 3",
            "Märklin Central Station 3plus",
        )
        override val defaultPort = CsCodec.TCP_PORT
        override val options = listOf(
            DriverOption(
                "loco", "Lokprotokoll",
                listOf("mm" to "Motorola", "mfx" to "mfx (Adresse wie in der CS)", "dcc" to "DCC"),
            ),
            DriverOption("accessory", "Weichen", listOf("mm" to "Motorola", "dcc" to "DCC")),
        )

        override fun create(config: StationConfig): CommandStation {
            val name = "${config.model ?: "Märklin CS"} (${config.host})"
            return CsStation(
                name = name,
                streamFactory = { TcpByteStream.open(config.host, config.port ?: CsCodec.TCP_PORT, name) },
                locoProtocol = CsProtocol.of(config.options["loco"]),
                accessoryProtocol = CsProtocol.of(config.options["accessory"]),
            )
        }
    }

    val ecos: StationDriver = object : StationDriver {
        override val id = "ecos"
        override val title = "ESU ECoS"
        override val devices = "ECoS 1 und 2, Märklin CS1 Reloaded"
        override val models = listOf(
            "ESU ECoS 1",
            "ESU ECoS 2",
            "Märklin Central Station 1 Reloaded",
        )
        override val defaultPort = EcosStation.PORT
        override val options = listOf(
            DriverOption(
                "loco", "Neue Loks anlegen als",
                listOf("DCC128" to "DCC 128", "DCC28" to "DCC 28", "MM28" to "Motorola 28"),
            ),
            DriverOption("accessory", "Weichen", listOf("DCC" to "DCC", "MOT" to "Motorola")),
        )

        override fun create(config: StationConfig): CommandStation {
            val name = "${config.model ?: "ESU ECoS"} (${config.host})"
            return EcosStation(
                name = name,
                streamFactory = { TcpByteStream.open(config.host, config.port ?: EcosStation.PORT, name) },
                locoProtocol = config.options["loco"] ?: "DCC128",
                accessoryProtocol = config.options["accessory"] ?: "DCC",
            )
        }
    }

    val dccEx: StationDriver = object : StationDriver {
        override val id = "dccex"
        override val title = "DCC-EX"
        override val devices = "DCC-EX-Eigenbau mit WLAN oder Ethernet"
        override val models = listOf(
            "DCC-EX (WLAN oder Ethernet)",
        )
        override val defaultPort = DccExStation.PORT
        override fun create(config: StationConfig): CommandStation {
            val name = "DCC-EX (${config.host})"
            return DccExStation(name, { TcpByteStream.open(config.host, config.port ?: DccExStation.PORT, name) })
        }
    }

    val xpressNet: StationDriver = object : StationDriver {
        override val id = "xpressnet-lan"
        override val title = "Lenz XpressNet (LAN)"
        override val devices = "LZV100, LZV200 und andere XpressNet-Zentralen am LAN/USB-Interface 23151"
        override val models = listOf(
            "Lenz LZV100 (LAN-Interface 23151)",
            "Lenz LZV200 (LAN-Interface 23151)",
            "Andere XpressNet-Zentrale am Lenz-LAN-Interface",
        )
        override val defaultPort = XpressNetCodec.PORT
        override fun create(config: StationConfig): CommandStation {
            val name = "${config.model ?: "Lenz XpressNet"} (${config.host})"
            return XpressNetStation(name, { TcpByteStream.open(config.host, config.port ?: XpressNetCodec.PORT, name) })
        }
    }

    /** Reihenfolge im Verbindungsdialog. */
    val drivers: List<StationDriver> = listOf(simulator, z21, marklinCs, ecos, dccEx, xpressNet)

    val discovery: StationDiscovery = NetworkDiscovery()
}
