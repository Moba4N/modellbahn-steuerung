package de.moba.modules.symbols.api

import de.moba.modules.symbols.api.ArmPosition.HANGING
import de.moba.modules.symbols.api.ArmPosition.HORIZONTAL
import de.moba.modules.symbols.api.ArmPosition.UP
import de.moba.modules.symbols.api.FormPart.Arm
import de.moba.modules.symbols.api.FormPart.Disc
import de.moba.modules.symbols.api.FormPart.ShuntDisc
import de.moba.modules.symbols.api.FormPart.Wing

/** Alte Formsignale (Tagesbild). Das erste Bild ist immer Halt, das zweite Fahrt. */
internal object FormSignalData {
    /** Nach Fachwissen gezeichnet, aber nicht anhand von Signalbuch oder Vorbildfotos geprueft. */
    val uncheckedSystems = setOf("ch-form-hs", "ch-form-vs", "nl-arm")

    private fun form(vararg parts: FormPart) = parts.toList()

    val systems: List<SignalSystem> = listOf(
        SignalSystem(
            id = "de-form-hp2", country = "DE", name = "Formhauptsignal zweiflügelig", kind = SignalKind.MAIN,
            columns = 1, rows = 1, lamps = emptyList(),
            aspects = listOf(
                SignalAspect("hp0", "Hp 0", "Halt", form = form(Arm(0, HORIZONTAL), Arm(1, HANGING))),
                SignalAspect("hp1", "Hp 1", "Fahrt", form = form(Arm(0, UP), Arm(1, HANGING))),
                SignalAspect("hp2", "Hp 2", "Langsamfahrt", form = form(Arm(0, UP), Arm(1, UP))),
            ),
            source = "Signalbuch DS 301/DV 301, Signale Hp 0 bis Hp 2 (Formsignale)",
        ),
        SignalSystem(
            id = "de-form-hp1", country = "DE", name = "Formhauptsignal einflügelig", kind = SignalKind.MAIN,
            columns = 1, rows = 1, lamps = emptyList(),
            aspects = listOf(
                SignalAspect("hp0", "Hp 0", "Halt", form = form(Arm(0, HORIZONTAL))),
                SignalAspect("hp1", "Hp 1", "Fahrt", form = form(Arm(0, UP))),
            ),
            source = "Signalbuch DS 301/DV 301, Signale Hp 0 und Hp 1 (Formsignale)",
        ),
        SignalSystem(
            id = "de-form-vr", country = "DE", name = "Formvorsignal mit Zusatzflügel", kind = SignalKind.DISTANT,
            columns = 1, rows = 1, lamps = emptyList(),
            aspects = listOf(
                SignalAspect("vr0", "Vr 0", "Halt erwarten", form = form(Disc(facing = true), Wing(diagonal = false))),
                SignalAspect("vr1", "Vr 1", "Fahrt erwarten", form = form(Disc(facing = false), Wing(diagonal = true))),
                SignalAspect("vr2", "Vr 2", "Langsamfahrt erwarten", form = form(Disc(facing = true), Wing(diagonal = true))),
            ),
            source = "Signalbuch DS 301/DV 301, Signale Vr 0 bis Vr 2 (Formsignale)",
        ),
        SignalSystem(
            id = "de-form-vr1", country = "DE", name = "Formvorsignal ohne Zusatzflügel", kind = SignalKind.DISTANT,
            columns = 1, rows = 1, lamps = emptyList(),
            aspects = listOf(
                SignalAspect("vr0", "Vr 0", "Halt erwarten", form = form(Disc(facing = true))),
                SignalAspect("vr1", "Vr 1", "Fahrt erwarten", form = form(Disc(facing = false))),
            ),
            source = "Signalbuch DS 301/DV 301, Signale Vr 0 und Vr 1 (Formsignale)",
        ),
        SignalSystem(
            id = "de-form-sh", country = "DE", name = "Formsperrsignal", kind = SignalKind.SHUNTING,
            columns = 1, rows = 1, lamps = emptyList(),
            aspects = listOf(
                SignalAspect("sh0", "Sh 0", "Halt! Fahrverbot", form = form(ShuntDisc(clear = false))),
                SignalAspect("sh1", "Sh 1", "Fahrverbot aufgehoben", form = form(ShuntDisc(clear = true))),
            ),
            source = "Signalbuch DS 301/DV 301, Signale Sh 0 und Sh 1 (Formsignal)",
        ),
        SignalSystem(
            id = "ch-form-hs", country = "CH", name = "Formhauptsignal (SBB, historisch)", kind = SignalKind.MAIN,
            columns = 1, rows = 1, lamps = emptyList(),
            aspects = listOf(
                SignalAspect("halt", "Halt", "Halt", form = form(Arm(0, HORIZONTAL), Arm(1, HANGING))),
                SignalAspect("fahrt", "Fahrt", "Fahrt", form = form(Arm(0, UP), Arm(1, HANGING))),
                SignalAspect("fahrt-ablenkung", "Fahrt mit Ablenkung", "Fahrt mit Geschwindigkeitsbeschränkung", form = form(Arm(0, UP), Arm(1, UP))),
            ),
        ),
        SignalSystem(
            id = "ch-form-vs", country = "CH", name = "Formvorsignal (SBB, historisch)", kind = SignalKind.DISTANT,
            columns = 1, rows = 1, lamps = emptyList(),
            aspects = listOf(
                SignalAspect("warnung", "Warnung", "Halt am Hauptsignal erwarten", form = form(Disc(facing = true))),
                SignalAspect("fahrt", "Fahrt erwarten", "Hauptsignal zeigt Fahrt", form = form(Disc(facing = false))),
            ),
        ),
        SignalSystem(
            id = "nl-arm", country = "NL", name = "Armsein (NS, historisch)", kind = SignalKind.MAIN,
            columns = 1, rows = 1, lamps = emptyList(),
            aspects = listOf(
                SignalAspect("onveilig", "Onveilig", "Halt", form = form(Arm(0, HORIZONTAL))),
                SignalAspect("veilig", "Veilig", "Fahrt", form = form(Arm(0, UP))),
            ),
        ),
    )
}
