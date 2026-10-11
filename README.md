# Modellbahn-Steuerung

Modulare Steuerungssoftware fuer digitale Modellbahnen, fuer Android und Windows.
Das Architektur-Dokument beschreibt Module, Abo-Modell und Fahrplan der Entwicklung.

Stand: **Phase 1**. Kern, Simulator, Z21-Adapter (z. B. YaMoRC YD7001) und das Modul "Fahren".

## Aufbau

| Ordner | Inhalt |
| --- | --- |
| `core/model` | Datenmodell der Anlage: Loks, Weichen, Gleisabschnitte, s88-Kontakte |
| `core/station-api` | Einheitlicher Befehlssatz fuer jede Digitalzentrale (`CommandStation`) |
| `core/runtime` | Modul-Vertrag (`MobaModule`), Modul-Host, Ereignis-Bus, Lizenzpruefung |
| `core/ui-shell` | App-Rahmen mit Gleisspannung, Nothalt und Modulliste (Compose Multiplatform) |
| `adapters/simulator` | Virtuelle Zentrale ohne Hardware, inklusive s88-Belegtmelder |
| `adapters/z21` | Z21-LAN-Protokoll per UDP: YaMoRC YD7001, Roco/Fleischmann z21 und kompatible |
| `modules/driving` | Modul "Fahren": `api` (Schnittstelle), `impl` (Logik), `ui` (Fahrregler) |
| `apps/desktop` | Windows-App (Installer als MSI/EXE) |
| `apps/android` | Android-App |
| `tools/sandbox` | Startet Kern, Simulator und Beispielmodule auf der Konsole |

## Voraussetzungen

- JDK 17 oder neuer
- Fuer die Android-App: Android SDK (z. B. ueber Android Studio), Pfad in `local.properties` als `sdk.dir`

## Befehle

```bash
./gradlew build                          # alles bauen und testen
./gradlew -Pmoba.apps=false build        # nur Kern und Adapter, ohne Android SDK
./gradlew :tools:sandbox:run             # Sandbox: Module gegen den Simulator laufen lassen
./gradlew :tools:sandbox:run --args="z21 192.168.0.111"   # 60 s Meldungen einer echten Zentrale anzeigen
./gradlew :apps:desktop:run              # Windows-App starten
./gradlew :apps:desktop:packageMsi       # Windows-Installer bauen
./gradlew :apps:android:installDebug     # Android-App auf ein verbundenes Geraet spielen
```

## Ein neues Modul anlegen

1. `MobaModule` implementieren: `id`, `version`, `apiVersion`, `requires`, `licenseKey` (`null` = kostenlos).
2. In `start()` Dienste mit `ctx.provide(...)` anbieten und Ereignisse mit `ctx.events.on<...>(ctx.scope) { }` abonnieren.
3. Nur ueber `ctx.station` (Zentralen-API) mit der Hardware sprechen, nie direkt mit einem Adapter.
4. Zuerst in der Sandbox gegen den Simulator testen, dann in den Apps registrieren.

Lizenzen werden nur beim Start geprueft. Ein laufendes Modul wird nie wegen eines Abo-Endes angehalten,
und der Nothalt ist nie lizenzpflichtig.

## Z21-Zentrale verbinden (YaMoRC YD7001)

In der App unter Uebersicht > Zentrale "Z21-Protokoll" waehlen, die IP-Adresse der Zentrale eintragen und
"Verbinden" druecken. Die App merkt sich die Auswahl. Unter "Meldungen der Zentrale" erscheinen
Gleisspannung, Nothalt, Weichen und Belegtmelder.

**s88-Melder:** Die YD7001 meldet s88-Kontakte vermutlich als R-Bus-Rueckmelder. Die App rechnet
zwei R-Bus-Module (je 8 Eingaenge) in ein s88-Modul mit 16 Kontakten um. Ob das an der YD7001 stimmt,
ist noch an echter Hardware zu pruefen: Abschnitt belegen und schauen, ob Modul und Kontakt passen.
LocoNet-Belegtmelder werden ebenfalls ausgewertet.

## Naechste Schritte

- s88-Zuordnung an der YD7001 pruefen; falls noetig LocoNet-Adapter
- Phase 2: Gleisbild-Stellpult, Rueckmelder-Modul, Lokverwaltung
