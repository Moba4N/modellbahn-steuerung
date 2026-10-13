# Modellbahn-Steuerung

Modulare Steuerungssoftware fuer digitale Modellbahnen, fuer Android und Windows.
Das Architektur-Dokument beschreibt Module, Abo-Modell und Fahrplan der Entwicklung.

Stand: **Phase 1** plus Zentralen-Paket Stufe A. Kern, Simulator, Adapter fuer Z21, Maerklin CS, ESU ECoS,
DCC-EX und Lenz XpressNet sowie die Module "Fahren", "Fahrzeuge", "Decoder" und "Gleisbild".

## Aufbau

| Ordner | Inhalt |
| --- | --- |
| `core/model` | Datenmodell der Anlage: Loks, Weichen, Gleisabschnitte, s88-Kontakte |
| `core/station-api` | Einheitlicher Befehlssatz fuer jede Digitalzentrale (`CommandStation`) |
| `core/runtime` | Modul-Vertrag (`MobaModule`), Modul-Host, Ereignis-Bus, Lizenzpruefung |
| `core/ui-shell` | App-Rahmen mit Gleisspannung, Nothalt und Modulliste (Compose Multiplatform) |
| `adapters/simulator` | Virtuelle Zentrale ohne Hardware, inklusive s88-Belegtmelder |
| `adapters/net` | Gemeinsame Basis fuer Zentralen an TCP (Verbindung, Bestaetigungen, Testhilfen) |
| `adapters/z21` | Z21-LAN-Protokoll per UDP: YaMoRC YD7001, Roco/Fleischmann z21 und kompatible |
| `adapters/marklin-cs` | Maerklin Central Station 2, 3 und 3plus (CAN ueber TCP) |
| `adapters/ecos` | ESU ECoS 1 und 2, Maerklin CS1 Reloaded (ECoS-Textprotokoll) |
| `adapters/dccex` | DCC-EX-Eigenbau ueber WLAN/LAN |
| `adapters/xpressnet` | Lenz LZV100/LZV200 und andere XpressNet-Zentralen am LAN/USB-Interface 23151 |
| `adapters/catalog` | Liste aller Zentralen fuer den Verbindungsdialog und Netzwerksuche |
| `modules/driving` | Modul "Fahren": `api` (Schnittstelle), `impl` (Logik), `ui` (Fahrregler) |
| `modules/vehicles` | Modul "Fahrzeuge": Loks, Gueterwagen- und Personenwagengattungen anlegen |
| `modules/decoder` | Modul "Decoder": CV-Programmierung und Decoder-Datenbank |
| `modules/trackplan` | Modul "Gleisbild": Gleisplan-Editor mit allen Gleisformen und Stellpult im Betrieb |
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

In der App unter Uebersicht > Zentrale im Menue "Digitalzentrale" die YaMoRC YD7001 waehlen, die IP-Adresse eintragen und
"Verbinden" druecken. Die App merkt sich die Auswahl. Unter "Meldungen der Zentrale" erscheinen
Gleisspannung, Nothalt, Weichen und Belegtmelder.

**s88-Melder:** Die YD7001 meldet s88-Kontakte vermutlich als R-Bus-Rueckmelder. Die App rechnet
zwei R-Bus-Module (je 8 Eingaenge) in ein s88-Modul mit 16 Kontakten um. Ob das an der YD7001 stimmt,
ist noch an echter Hardware zu pruefen: Abschnitt belegen und schauen, ob Modul und Kontakt passen.
LocoNet-Belegtmelder werden ebenfalls ausgewertet.

## Unterstuetzte Zentralen

Alle Zentralen-Adapter sind kostenlos. Getestet sind sie mit nachgebauten Zentralen in den Tests;
an echter Hardware bisher nur die YaMoRC YD7001.

| Zentrale | Treiber | Port | Im Netz suchen |
| --- | --- | --- | --- |
| YaMoRC YD7001, Roco/Fleischmann Z21 und z21, Digikeijs DR5000 | Z21-Protokoll | UDP 21105 | ja |
| Maerklin Central Station 2, 3, 3plus | Maerklin Central Station | TCP 15731 | ja |
| ESU ECoS 1 und 2, Maerklin CS1 Reloaded | ESU ECoS | TCP 15471 | nein |
| DCC-EX (WLAN oder Ethernet) | DCC-EX | TCP 2560 | nein |
| Lenz LZV100/LZV200 am LAN/USB-Interface 23151 | Lenz XpressNet (LAN) | TCP 5550 | nein |

Hinweise:

- **Maerklin CS:** Loks werden ueber Adresse und Protokoll angesprochen (Motorola, mfx oder DCC). Bei mfx
  ist das die Adresse, die die CS fuer die Lok anzeigt. s88-Kontakte am Links88 werden fortlaufend gezaehlt.
- **ECoS:** Faehrt die App eine Adresse, die in der ECoS nicht angelegt ist, legt sie die Lok in der ECoS an.
  Die ECoS kennt keinen Lok-Nothalt bei eingeschaltetem Gleis; der Nothalt schaltet das Gleis ab.
- **DCC-EX:** Weichen und Nothalt bestaetigt die DCC-EX nicht; die App meldet sie, sobald der Befehl raus ist.
  Sensoren werden als Rueckmelder gezaehlt (Sensor 1 = Modul 1, Kontakt 1).
- **Lenz:** Nach jedem Fahrbefehl fragt die App den Lokzustand ab. Rueckmelder (LR101, 8 Eingaenge je
  Adresse) werden auf Module zu 16 Kontakten umgerechnet.

## Fahrzeuge

Drei Listen: Lokomotiven (Adresse, Baureihe, Bahnverwaltung, Epoche, Antrieb, Protokoll, Funktionsnamen),
Gueterwagengattungen und Personenwagengattungen (Gattungszeichen wie Eaos oder Bm 235, Bauart, Anzahl).
Fuer gaengige DB-Gattungen gibt es Vorlagen. Angelegte Loks erscheinen mit Namen im Fahrpult.
Jede Aenderung wird sofort in `fahrzeuge.txt` gespeichert (gleicher Ordner wie die Gleisbilder).

| | Kostenlos und Basis | Pro / Club |
|---|---|---|
| Eintraege je Liste | 3 | unbegrenzt |

Wer nach einem Abo-Wechsel mehr Eintraege hat als erlaubt, behaelt sie und kann sie bearbeiten,
aber keine neuen anlegen.

## Decoder

CVs lesen und schreiben ist kostenlos: auf dem Programmiergleis (lesen und schreiben) oder waehrend
der Fahrt auf dem Hauptgleis (POM, nur schreiben). Dazu gibt es "Decoder auslesen" (CV 1 bis 8, 17, 18,
19, 29 mit Hersteller und Adresse), "Adresse schreiben" (kurz oder lang samt CV 29) und die Schalter
von CV 29 einzeln.

Die Decoder-Datenbank sichert die Werte je Lok in `decoder.txt` und schreibt sie spaeter zurueck.
Sie braucht die Zusatzlizenz "Decoder-Datenbank" (Schluessel `decoder`, im Club-Abo enthalten).

Programmieren geht bisher mit Zentralen am Z21-Protokoll (YaMoRC YD7001, Roco z21) und im Simulator.
An echter Hardware ist es noch nicht geprueft.

## Gleisbild

Der Editor arbeitet auf einem Raster mit 45-Grad-Schritten. Gleisformen: Gerade, Kurve 45°, Bogen 90°,
Weiche links/rechts, Y-Weiche, Dreiwegweiche, Kreuzung 90° und 45°, einfache und doppelte Kreuzungsweiche,
Prellbock, Signal, Rueckmelder (s88-Modul und Kontakt), Entkuppler und Text.
Im Betrieb schaltet ein Tipp Weichen, Signale und Entkuppler; belegte Rueckmelder leuchten orange.

| | Kostenlos | Basis | Pro / Club |
|---|---|---|---|
| Gleisbilder zeichnen und ausprobieren | ja | ja | ja |
| Gleisbilder speichern | nein | ja | ja |
| Gleisbilder gleichzeitig anzeigen | 1 | 1 | bis zu 3 |

Die Gleisbilder liegen in `gleisbilder.txt` (Windows: `%USERPROFILE%\.modellbahn-steuerung`,
Android: App-Speicher). Ohne gespeicherte Datei startet die App mit einer Beispielanlage.
Eine Dreiwegweiche belegt zwei Adressen: die eingetragene fuer links, die naechste fuer rechts.

## Erscheinungsbild

Das Burger-Menue oben neben "Gleis an/aus" wechselt die Ansicht und oeffnet "Erscheinungsbild".
Dort lassen sich kostenlos einstellen:

- Design: Modern, Windows 11 (Fluent-Farben, kleine Ecken) oder Stellwerk, jeweils hell oder dunkel
- Schriftart (Standard, Serifenlos, Serifen, Schreibmaschine) und Schriftgroesse
- eigenes Hintergrundbild als Wasserzeichen in drei Staerken. Das Bild wird als Kopie
  `hintergrund.bild` im Datenordner gespeichert.

## Naechste Schritte

- s88-Zuordnung an der YD7001 pruefen; falls noetig LocoNet-Adapter
- Zentralen-Paket Stufe B (USB/seriell): Intellibox und Tams (P50X), Lenz LI-USB, LocoNet, Maerklin 6050
- Phase 2: Rueckmelder-Modul, Lokverwaltung; Fahrstrassen im Gleisbild
