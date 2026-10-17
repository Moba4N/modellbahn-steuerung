package de.moba.modules.symbols.api

// Lichtsignale, erzeugt aus signalsysteme.json (nur DE, CH, NL). Formsignale stehen in FormSignalData.
// Lampenanordnungen sind vereinfacht; Quellen und offene Fragen stehen im README unter "Signalbilder".
internal object SignalData {
    private fun l(col: Int, row: Int) = Lamp(col, row)
    private fun on(col: Int, row: Int, color: LampColor, flash: Boolean = false) = LitLamp(col, row, color, flash)

    val systems: List<SignalSystem> = listOf(
        SignalSystem(
            id = "de-hv-hp", country = "DE", name = "H/V-Lichthauptsignal (DB)", kind = SignalKind.MAIN,
            columns = 3, rows = 4,
            lamps = listOf(l(1, 0), l(0, 1), l(2, 1), l(1, 2), l(0, 3), l(1, 3)),
            aspects = listOf(
                SignalAspect("hp0", "Hp 0", "Halt", listOf(on(0, 1, LampColor.RED), on(2, 1, LampColor.RED))),
                SignalAspect("hp1", "Hp 1", "Fahrt", listOf(on(1, 0, LampColor.GREEN))),
                SignalAspect("hp2", "Hp 2", "Langsamfahrt (40 km/h, sofern nicht anders angezeigt)", listOf(on(1, 0, LampColor.GREEN), on(1, 3, LampColor.YELLOW))),
                SignalAspect("hp2-zs3", "Hp 2 + Zs 3", "Langsamfahrt mit angezeigter Geschwindigkeit (hier 60 km/h)", listOf(on(1, 0, LampColor.GREEN), on(1, 3, LampColor.YELLOW)), Indicator("6", LampColor.WHITE)),
                SignalAspect("sh1", "Hp 0 + Sh 1", "Halt für Zugfahrten, Rangierfahrt erlaubt", listOf(on(2, 1, LampColor.RED), on(0, 3, LampColor.WHITE), on(1, 2, LampColor.WHITE))),
                SignalAspect("zs1", "Hp 0 + Zs 1", "Ersatzsignal: am Halt zeigenden Signal ohne schriftlichen Befehl vorbeifahren", listOf(on(0, 1, LampColor.RED), on(2, 1, LampColor.RED), on(0, 3, LampColor.WHITE, flash = true))),
            ),
            source = "https://de.wikipedia.org/wiki/H/V-Signalsystem",
        ),
        SignalSystem(
            id = "de-hv-vr", country = "DE", name = "H/V-Lichtvorsignal (DB)", kind = SignalKind.DISTANT,
            columns = 2, rows = 4,
            lamps = listOf(l(0, 0), l(1, 0), l(1, 1), l(0, 2), l(0, 3)),
            aspects = listOf(
                SignalAspect("vr0", "Vr 0", "Halt erwarten", listOf(on(0, 3, LampColor.YELLOW), on(1, 1, LampColor.YELLOW))),
                SignalAspect("vr1", "Vr 1", "Fahrt erwarten", listOf(on(0, 2, LampColor.GREEN), on(1, 0, LampColor.GREEN))),
                SignalAspect("vr2", "Vr 2", "Langsamfahrt erwarten", listOf(on(0, 3, LampColor.YELLOW), on(1, 0, LampColor.GREEN))),
                SignalAspect("vr2-zs3v", "Vr 2 + Zs 3v", "Geschwindigkeitsbeschränkung erwarten (hier 60 km/h)", listOf(on(0, 3, LampColor.YELLOW), on(1, 0, LampColor.GREEN)), Indicator("6", LampColor.YELLOW)),
                SignalAspect("vr0-kurz", "Vr 0 + Zusatzlicht", "Halt erwarten, Bremsweg um mehr als 5 % verkürzt", listOf(on(0, 3, LampColor.YELLOW), on(1, 1, LampColor.YELLOW), on(0, 0, LampColor.WHITE))),
            ),
            source = "https://de.wikipedia.org/wiki/H/V-Signalsystem",
        ),
        SignalSystem(
            id = "de-ks", country = "DE", name = "Ks-Mehrabschnittssignal (DB)", kind = SignalKind.COMBINED,
            columns = 3, rows = 4,
            lamps = listOf(l(0, 0), l(1, 0), l(0, 1), l(2, 1), l(1, 2), l(0, 3)),
            aspects = listOf(
                SignalAspect("hp0", "Hp 0", "Halt", listOf(on(1, 0, LampColor.RED))),
                SignalAspect("ks1", "Ks 1", "Fahrt", listOf(on(0, 1, LampColor.GREEN))),
                SignalAspect("ks1-zs3", "Ks 1 + Zs 3", "Fahrt mit angezeigter Geschwindigkeit (hier 80 km/h)", listOf(on(0, 1, LampColor.GREEN)), Indicator("8", LampColor.WHITE)),
                SignalAspect("ks1-blink", "Ks 1 blinkend + Zs 3v", "Fahrt, am nächsten Signal Geschwindigkeitsbeschränkung erwarten (hier 60 km/h)", listOf(on(0, 1, LampColor.GREEN, flash = true)), Indicator("6", LampColor.YELLOW)),
                SignalAspect("ks2", "Ks 2", "Halt erwarten", listOf(on(2, 1, LampColor.YELLOW))),
                SignalAspect("sh1", "Hp 0 + Sh 1/Ra 12", "Halt für Zugfahrten, Rangierfahrt erlaubt", listOf(on(1, 0, LampColor.RED), on(0, 3, LampColor.WHITE), on(1, 2, LampColor.WHITE))),
                SignalAspect("zs1", "Hp 0 + Zs 1", "Ersatzsignal", listOf(on(1, 0, LampColor.RED), on(0, 3, LampColor.WHITE, flash = true))),
                SignalAspect("kennlicht", "Kennlicht", "Signal betrieblich abgeschaltet", listOf(on(0, 0, LampColor.WHITE))),
            ),
            source = "https://de.wikipedia.org/wiki/Ks-Signalsystem",
        ),
        SignalSystem(
            id = "de-hl", country = "DE", name = "Hl-Lichtsignale (ehem. DR)", kind = SignalKind.COMBINED,
            columns = 3, rows = 5,
            lamps = listOf(l(0, 0), l(2, 0), l(1, 1), l(0, 2), l(2, 2), l(1, 2), l(0, 3), l(0, 4), l(1, 4), l(2, 4)),
            aspects = listOf(
                SignalAspect("hp0", "Hp 0 (früher Hl 13)", "Halt", listOf(on(1, 1, LampColor.RED))),
                SignalAspect("hl1", "Hl 1", "Fahrt mit Höchstgeschwindigkeit", listOf(on(2, 0, LampColor.GREEN))),
                SignalAspect("hl2", "Hl 2", "Fahrt mit 100 km/h, dann Höchstgeschwindigkeit", listOf(on(2, 0, LampColor.GREEN), on(0, 2, LampColor.YELLOW), on(0, 4, LampColor.GREEN), on(1, 4, LampColor.GREEN), on(2, 4, LampColor.GREEN))),
                SignalAspect("hl3a", "Hl 3a", "Fahrt mit 40 km/h, dann Höchstgeschwindigkeit", listOf(on(2, 0, LampColor.GREEN), on(0, 2, LampColor.YELLOW))),
                SignalAspect("hl3b", "Hl 3b", "Fahrt mit 60 km/h, dann Höchstgeschwindigkeit", listOf(on(2, 0, LampColor.GREEN), on(0, 2, LampColor.YELLOW), on(0, 4, LampColor.YELLOW), on(1, 4, LampColor.YELLOW), on(2, 4, LampColor.YELLOW))),
                SignalAspect("hl4", "Hl 4", "Höchstgeschwindigkeit auf 100 km/h ermäßigen", listOf(on(2, 0, LampColor.GREEN, flash = true))),
                SignalAspect("hl5", "Hl 5", "Fahrt mit 100 km/h, 100 km/h erwarten", listOf(on(2, 0, LampColor.GREEN, flash = true), on(0, 2, LampColor.YELLOW), on(0, 4, LampColor.GREEN), on(1, 4, LampColor.GREEN), on(2, 4, LampColor.GREEN))),
                SignalAspect("hl6a", "Hl 6a", "Fahrt mit 40 km/h, 100 km/h erwarten", listOf(on(2, 0, LampColor.GREEN, flash = true), on(0, 2, LampColor.YELLOW))),
                SignalAspect("hl6b", "Hl 6b", "Fahrt mit 60 km/h, 100 km/h erwarten", listOf(on(2, 0, LampColor.GREEN, flash = true), on(0, 2, LampColor.YELLOW), on(0, 4, LampColor.YELLOW), on(1, 4, LampColor.YELLOW), on(2, 4, LampColor.YELLOW))),
                SignalAspect("hl7", "Hl 7", "Höchstgeschwindigkeit auf 40/60 km/h ermäßigen", listOf(on(0, 0, LampColor.YELLOW, flash = true))),
                SignalAspect("hl8", "Hl 8", "Fahrt mit 100 km/h, 40/60 km/h erwarten", listOf(on(0, 0, LampColor.YELLOW, flash = true), on(0, 2, LampColor.YELLOW), on(0, 4, LampColor.GREEN), on(1, 4, LampColor.GREEN), on(2, 4, LampColor.GREEN))),
                SignalAspect("hl9a", "Hl 9a", "Fahrt mit 40 km/h, 40/60 km/h erwarten", listOf(on(0, 0, LampColor.YELLOW, flash = true), on(0, 2, LampColor.YELLOW))),
                SignalAspect("hl9b", "Hl 9b", "Fahrt mit 60 km/h, 40/60 km/h erwarten", listOf(on(0, 0, LampColor.YELLOW, flash = true), on(0, 2, LampColor.YELLOW), on(0, 4, LampColor.YELLOW), on(1, 4, LampColor.YELLOW), on(2, 4, LampColor.YELLOW))),
                SignalAspect("hl10", "Hl 10", "Halt erwarten", listOf(on(0, 0, LampColor.YELLOW))),
                SignalAspect("hl11", "Hl 11", "Fahrt mit 100 km/h, Halt erwarten", listOf(on(0, 0, LampColor.YELLOW), on(0, 2, LampColor.YELLOW), on(0, 4, LampColor.GREEN), on(1, 4, LampColor.GREEN), on(2, 4, LampColor.GREEN))),
                SignalAspect("hl12a", "Hl 12a", "Fahrt mit 40 km/h, Halt erwarten", listOf(on(0, 0, LampColor.YELLOW), on(0, 2, LampColor.YELLOW))),
                SignalAspect("hl12b", "Hl 12b", "Fahrt mit 60 km/h, Halt erwarten", listOf(on(0, 0, LampColor.YELLOW), on(0, 2, LampColor.YELLOW), on(0, 4, LampColor.YELLOW), on(1, 4, LampColor.YELLOW), on(2, 4, LampColor.YELLOW))),
                SignalAspect("sh1", "Hp 0 + Sh 1/Ra 12", "Halt für Zugfahrten, Rangierfahrt erlaubt", listOf(on(1, 1, LampColor.RED), on(0, 3, LampColor.WHITE), on(1, 2, LampColor.WHITE))),
                SignalAspect("zs1", "Hp 0 + Zs 1", "Ersatzsignal", listOf(on(1, 1, LampColor.RED), on(0, 3, LampColor.WHITE, flash = true))),
            ),
            source = "https://en.wikipedia.org/wiki/German_railway_signalling",
        ),
        SignalSystem(
            id = "de-sh", country = "DE", name = "Lichtsperrsignal Sh/Ra 12 (DB)", kind = SignalKind.SHUNTING,
            columns = 2, rows = 3,
            lamps = listOf(l(1, 0), l(0, 1), l(1, 1), l(0, 2)),
            aspects = listOf(
                SignalAspect("hp0", "Hp 0 (früher Sh 0)", "Halt! Fahrverbot", listOf(on(0, 1, LampColor.RED), on(1, 1, LampColor.RED))),
                SignalAspect("sh1", "Sh 1 / Ra 12", "Fahrverbot aufgehoben (Rangierfahrt erlaubt)", listOf(on(0, 2, LampColor.WHITE), on(1, 0, LampColor.WHITE))),
            ),
            source = "https://de.wikipedia.org/wiki/Sperrsignal",
        ),
        SignalSystem(
            id = "ch-l-hs", country = "CH", name = "System L – Hauptsignal (SBB)", kind = SignalKind.MAIN,
            columns = 2, rows = 5,
            lamps = listOf(l(0, 1), l(1, 0), l(1, 1), l(1, 2), l(1, 3), l(1, 4)),
            aspects = listOf(
                SignalAspect("halt", "Halt", "Halt", listOf(on(0, 1, LampColor.RED))),
                SignalAspect("fb1", "Fahrbegriff 1", "Fahrt mit Streckenhöchstgeschwindigkeit", listOf(on(1, 0, LampColor.GREEN))),
                SignalAspect("fb2", "Fahrbegriff 2", "Fahrt mit 40 km/h", listOf(on(1, 0, LampColor.GREEN), on(1, 1, LampColor.YELLOW))),
                SignalAspect("fb3", "Fahrbegriff 3", "Fahrt mit 60 km/h", listOf(on(1, 0, LampColor.GREEN), on(1, 2, LampColor.GREEN))),
                SignalAspect("fb5", "Fahrbegriff 5", "Fahrt mit 90 km/h", listOf(on(1, 0, LampColor.GREEN), on(1, 2, LampColor.GREEN), on(1, 4, LampColor.GREEN))),
                SignalAspect("fb6", "Fahrbegriff 6", "Kurze Fahrt / Fahrt mit 40 km/h auf Sicht", listOf(on(1, 1, LampColor.YELLOW), on(1, 3, LampColor.YELLOW))),
                SignalAspect("hilfs", "Hilfssignal (neu)", "Vorbeifahrt am Halt zeigenden Signal, Fahrt auf Sicht", listOf(on(0, 1, LampColor.RED, flash = true))),
            ),
            source = "https://en.wikipedia.org/wiki/Swiss_railway_signalling",
        ),
        SignalSystem(
            id = "ch-l-vs", country = "CH", name = "System L – Vorsignal (SBB)", kind = SignalKind.DISTANT,
            columns = 3, rows = 3,
            lamps = listOf(l(0, 0), l(2, 0), l(2, 1), l(0, 2), l(2, 2)),
            aspects = listOf(
                SignalAspect("warnung", "Warnung", "Halt am nächsten Hauptsignal erwarten", listOf(on(0, 0, LampColor.YELLOW), on(2, 0, LampColor.YELLOW))),
                SignalAspect("ank1", "Ankündigung Fahrbegriff 1", "Fahrt mit Höchstgeschwindigkeit erwarten", listOf(on(2, 1, LampColor.GREEN), on(0, 2, LampColor.GREEN))),
                SignalAspect("ank2", "Ankündigung Fahrbegriff 2", "40 km/h erwarten", listOf(on(0, 0, LampColor.YELLOW), on(2, 1, LampColor.GREEN))),
                SignalAspect("ank3", "Ankündigung Fahrbegriff 3", "60 km/h erwarten", listOf(on(0, 0, LampColor.YELLOW), on(2, 1, LampColor.GREEN), on(0, 2, LampColor.GREEN))),
                SignalAspect("ank5", "Ankündigung Fahrbegriff 5", "90 km/h erwarten", listOf(on(2, 1, LampColor.GREEN), on(0, 2, LampColor.GREEN), on(2, 2, LampColor.YELLOW))),
            ),
            source = "https://en.wikipedia.org/wiki/Swiss_railway_signalling",
        ),
        SignalSystem(
            id = "ch-n", country = "CH", name = "System N (SBB)", kind = SignalKind.COMBINED,
            columns = 3, rows = 2,
            lamps = listOf(l(1, 0), l(0, 1), l(2, 1)),
            aspects = listOf(
                SignalAspect("halt", "Halt", "Halt", listOf(on(1, 0, LampColor.RED))),
                SignalAspect("fahrt", "Fahrt", "Fahrt mit Streckenhöchstgeschwindigkeit", listOf(on(2, 1, LampColor.GREEN))),
                SignalAspect("ausfuehrung", "Ausführung", "Fahrt mit angezeigter Geschwindigkeit (hier 40 km/h)", listOf(on(2, 1, LampColor.GREEN)), Indicator("4", LampColor.WHITE)),
                SignalAspect("warnung", "Warnung", "Halt am nächsten Signal erwarten", listOf(on(0, 1, LampColor.YELLOW))),
                SignalAspect("ankuendigung", "Ankündigung", "Angezeigte Geschwindigkeit am nächsten Signal erwarten (hier 80 km/h)", listOf(on(0, 1, LampColor.YELLOW)), Indicator("8", LampColor.WHITE)),
                SignalAspect("hilfs", "Hilfssignal", "Vorbeifahrt am Halt zeigenden Signal, Fahrt auf Sicht", listOf(on(1, 0, LampColor.RED, flash = true))),
            ),
            source = "https://en.wikipedia.org/wiki/Swiss_railway_signalling",
        ),
        SignalSystem(
            id = "ch-zwerg", country = "CH", name = "Zwergsignal (SBB)", kind = SignalKind.SHUNTING,
            columns = 2, rows = 2,
            lamps = listOf(l(1, 0), l(0, 1), l(1, 1)),
            aspects = listOf(
                SignalAspect("halt", "Halt", "Halt", listOf(on(0, 1, LampColor.WHITE), on(1, 1, LampColor.WHITE))),
                SignalAspect("vorsicht", "Fahrt mit Vorsicht", "Fahrt, Hindernis jederzeit möglich", listOf(on(0, 1, LampColor.WHITE), on(1, 0, LampColor.WHITE))),
                SignalAspect("fahrt", "Fahrt", "Fahrt", listOf(on(1, 0, LampColor.WHITE), on(1, 1, LampColor.WHITE))),
            ),
            source = "https://en.wikipedia.org/wiki/Swiss_railway_signalling",
        ),
        SignalSystem(
            id = "nl-main", country = "NL", name = "Lichtsein (ProRail/NS)", kind = SignalKind.COMBINED,
            columns = 1, rows = 3,
            lamps = listOf(l(0, 0), l(0, 1), l(0, 2)),
            aspects = listOf(
                SignalAspect("rood", "Rood", "Halt", listOf(on(0, 2, LampColor.RED))),
                SignalAspect("groen", "Groen", "Fahrt mit Streckengeschwindigkeit", listOf(on(0, 0, LampColor.GREEN))),
                SignalAspect("groen-knip", "Groen knipperend", "Fahrt mit max. 40 km/h", listOf(on(0, 0, LampColor.GREEN, flash = true))),
                SignalAspect("groen-knip-6", "Groen knipperend + 6", "Fahrt mit max. 60 km/h", listOf(on(0, 0, LampColor.GREEN, flash = true)), Indicator("6", LampColor.GREEN)),
                SignalAspect("geel", "Geel", "Bis zum nächsten Signal auf 40 km/h verlangsamen", listOf(on(0, 1, LampColor.YELLOW))),
                SignalAspect("geel-8", "Geel + 8", "Bis zum nächsten Signal auf 80 km/h verlangsamen", listOf(on(0, 1, LampColor.YELLOW)), Indicator("8", LampColor.YELLOW)),
                SignalAspect("geel-knip", "Geel knipperend", "Fahrt auf Sicht (Gleis kann besetzt sein)", listOf(on(0, 1, LampColor.YELLOW, flash = true))),
            ),
            source = "https://en.wikipedia.org/wiki/Dutch_railway_signalling",
        ),
    )
}
