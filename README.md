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
| `modules/symbols` | Modul "Symbole": Pixel-Editor fuer eigene Symbole, Licht- und Formsignale (DE, CH, NL) |
| `modules/throttles` | Modul "Mehrere Fahrregler": Leiste mit bis zu sechs Fahrreglern am unteren Rand (ab Pro) |
| `modules/calibration` | Modul "Loks einmessen": Geschwindigkeit je Fahrstufe (14, 28, 128) ueber eine Messstrecke (ab Pro) |
| `modules/routes` | Modul "Fahrstraßen": Fahrstrassen (stellen ab Basis), Pendelstrecken und Zugrouten (fahren ab Pro) |
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
Prellbock, Signal, Rueckmelder (s88-Modul und Kontakt), Entkuppler, Text und Symbol.
Signale arbeiten wie in iTrain: Jedem Signal laesst sich ein Signaltyp aus dem Katalog oder ein eigenes
Signalsymbol zuweisen. Es steht neben dem Gleis und dreht sich mit ihm. Im Betrieb oeffnet ein Klick auf ein
Signal mit mehr als zwei Bildern die Liste aller Signalbilder; Signale mit zwei Bildern wechseln direkt.
Geschaltet wird mit Adresspaaren ab der eingetragenen Grundadresse: Bild 1 = Adresse rot, Bild 2 = Adresse gruen,
Bild 3 = Adresse + 1 rot, Bild 4 = Adresse + 1 gruen usw. (bis 8 Bilder). Die Zuordnung steht im Editor
unter dem Signal. Meldet die Zentrale eine dieser Adressen, zeigt das Gleisbild das passende Bild.
Eine Symbolkachel zeigt ein eigenes Symbol, mit Schaltadresse wechselt sie zwischen
erstem (aus) und zweitem Zustand (an).
Im Betrieb schaltet ein Tipp Weichen, Signale und Entkuppler; belegte Rueckmelder leuchten orange.
Die Gleisgroesse (8 bis 80, anfangs 24) laesst sich im Betrieb und im Editor mit Minus/Plus einstellen und bleibt
gespeichert; "Einpassen" waehlt die Groesse, bei der das ganze Raster ins Fenster passt.
Auch die Symbole in der Leiste "Gleisformen" lassen sich mit Minus/Plus verkleinern oder vergroessern (20 bis 64).
Sind Gleisbilder beim Schliessen (Windows) oder Zurueck (Android) ungespeichert, fragt die App nach.

| | Kostenlos | Basis | Pro / Club |
|---|---|---|---|
| Gleisbilder zeichnen und ausprobieren | ja | ja | ja |
| Gleisbilder speichern | nein | ja | ja |
| Gleisbilder gleichzeitig anzeigen | 1 | 1 | bis zu 3 |

Die Gleisbilder liegen in `gleisbilder.txt` (Windows: `%USERPROFILE%\.modellbahn-steuerung`,
Android: App-Speicher). Ohne gespeicherte Datei startet die App mit einer Beispielanlage.
Eine Dreiwegweiche belegt zwei Adressen: die eingetragene fuer links, die naechste fuer rechts.

## Fahrpult

Am PC ist das Fahrpult rechts anfangs ausgeblendet. Der Tacho-Knopf oben neben "Gleis an/aus" blendet es ein
und aus; die Wahl bleibt gespeichert. Ohne eingeblendetes Fahrpult zeigt der Reiter "Fahren" es ueber der Lokliste.

## Mehrere Fahrregler

Ab Pro gibt es am unteren Bildrand eine Leiste "Fahrregler". Mit "+ Fahrregler" kommt eine Lok aus der Liste
oder per Adresse dazu, bis zu sechs gleichzeitig. Jeder Regler hat Fahrstufe, Richtung, Halt und Licht.
Die Leiste laesst sich einklappen; die offenen Regler bleiben in `fahrregler.txt` gespeichert.

## Loks einmessen

Ab Pro misst der Reiter "Einmessen", wie schnell eine Lok in jeder Fahrstufe faehrt (Vorbild-km/h).
Dazu braucht es einen Rundkurs mit zwei Rueckmeldern; die Laenge der Messstrecke wird von Anfang Startmelder
bis Anfang Endmelder gemessen. Fuer jede Stufe faehrt die Lok an, wartet die Anlaufzeit ab und misst die Zeit
zwischen den Meldern. 14 und 28 Fahrstufen werden komplett gemessen, bei 128 jede 4., 8. oder 16. Stufe.
Die Zentrale faehrt intern immer mit 128 Fahrstufen; fuer 14 und 28 nimmt die App die passenden Stellen dieser Skala.
Kommt ein Melder nicht innerhalb von 180 Sekunden, gilt die Stufe als zu langsam. Ergebnis: Kurve, Tabelle,
Hoechstgeschwindigkeit und "ab welcher Fahrstufe faehrt die Lok X km/h". Gespeichert in `einmessen.txt`.

## Fahrstrassen, Pendelstrecken, Zugrouten

Der Reiter "Fahrstraßen" hat drei Bereiche. Anlegen und Bearbeiten ist in jeder Version moeglich.

- **Fahrstrassen** (stellen ab Basis): eine Liste von Weichen- und Signaladressen mit gerade/abzweig, dazu ein
  optionaler Start- und ein Zielmelder (s88 als `Modul.Kontakt`, z. B. `1.3`). "Stellen" schaltet die Weichen
  nacheinander mit 150 ms Pause. Eine gestellte Fahrstrasse sperrt ihre Adressen: eine zweite Fahrstrasse mit
  einer gemeinsamen Weiche laesst sich erst stellen, wenn die erste aufgeloest ist. Wird der Zielmelder belegt,
  loest sie sich von selbst auf.
- **Pendelstrecken** (ab Pro): eine Lok faehrt vorwaerts bis Endmelder B, haelt, wartet und faehrt rueckwaerts bis
  Endmelder A, immer wieder. Optionale Bremsmelder schalten vorher auf die Bremsstufe.
- **Zugrouten** (ab Pro): eine Lok faehrt nacheinander ueber mehrere Fahrstrassen. Jeder Abschnitt stellt seine
  Fahrstrasse, sobald sie frei ist, faehrt bis zum Haltemelder (leer = Zielmelder der Fahrstrasse), haelt, loest auf
  und wartet. Mit "Immer wieder von vorn" faehrt die Route im Kreis.

**Aus dem Gleisbild:** In jedem Editor oeffnet "Im Gleisbild wählen" das Gleisbild. Fuer eine Fahrstrasse Start und
Ziel antippen: die App sucht alle befahrbaren Wege (kuerzester zuerst), zeigt sie mit gestellten Weichen an und
uebernimmt Weichenadressen samt Stellung, ersten und letzten Rueckmelder als Start und Ziel. Fuer eine Pendelstrecke
die beiden Endmelder antippen (Bremsmelder = die Melder direkt davor). Fuer eine Zugroute die Halte nacheinander
antippen; jeder Abschnitt wird eine Fahrstrasse (vorhandene mit gleichem Weg werden wiederverwendet).

"Stopp" haelt die Lok sofort an und gibt alle Fahrstrassen der Automatik frei. Eine Lok kann nur in einer Automatik
gleichzeitig fahren. Gespeichert in `fahrstrassen.txt`.

## Symbole

Der Symbol-Editor arbeitet wie in TrainController: quadratische Pixelbilder (16, 24, 32 oder 48 Pixel)
mit mehreren Zustaenden, z. B. "aus" und "an" oder je Signalbild einer. Werkzeuge: Stift, Radierer, Linie,
Rechteck, Ellipse (je offen und gefuellt), Flaeche fuellen, Farbe aufnehmen; dazu Spiegeln, Drehen,
Verschieben, Rueckgaengig/Wiederholen, Palette mit Signalfarben und eigene Farbe als Hex-Wert.

Unter "Signalbilder" stehen Signale aus Deutschland, der Schweiz und den Niederlanden:
Lichtsignale (H/V, Ks, Hl, Sh; SBB System L und N, Zwergsignal; NS-Lichtsein) und Formsignale
(Formhaupt- und Formvorsignal ein- und zweifluegelig, Formsperrsignal; SBB-Formsignale und NS-Armsein, historisch).
Jedes System laesst sich als eigenes Symbol uebernehmen und weiter anpassen.
Farben und Bedeutungen folgen den Signalbuechern, die Darstellung ist vereinfacht. Die Schweizer Formsignale
und das NS-Armsein sind nach Fachwissen gezeichnet und noch nicht anhand von Vorbildern geprueft (in der App markiert).
Daten: `SignalData.kt` (Lichtsignale) und `FormSignalData.kt` (Formsignale) in `modules/symbols/api`.

| | Kostenlos | Basis | Pro / Club |
|---|---|---|---|
| Symbole zeichnen, Signalbilder nutzen | ja | ja | ja |
| Eigene Symbole speichern | nein | nein | ja |

Die Symbole liegen in `symbole.txt` neben den Gleisbildern.

## Erscheinungsbild

Das Burger-Menue oben neben "Gleis an/aus" wechselt die Ansicht und oeffnet "Erscheinungsbild".
Dort lassen sich kostenlos einstellen:

- Design: Modern, Windows 11 (Fluent-Farben, kleine Ecken) oder Stellwerk, jeweils hell oder dunkel
- Schriftart (Standard, Serifenlos, Serifen, Schreibmaschine) und Schriftgroesse
- eigenes Hintergrundbild als Wasserzeichen in drei Staerken. Das Bild wird als Kopie
  `hintergrund.bild` im Datenordner gespeichert.

## Microsoft Store (MSIX)

Der GitHub-Build legt unter "Actions" den Download `windows-msix` ab:

- `Modellbahn-Steuerung-0.1.0.0.msix`: unsigniert, zum Hochladen im Partner Center (Microsoft signiert es).
- `Modellbahn-Steuerung-0.1.0.0-test.msix` mit `Modellbahn-Steuerung-Test.cer`: zum Ausprobieren ohne Store.

Paketname und Herausgeber aus dem Partner Center ("Produktidentitaet") als Repository-Variablen
`MSIX_IDENTITY_NAME`, `MSIX_PUBLISHER` und `MSIX_PUBLISHER_DISPLAY_NAME` eintragen. Vorlage und Skript liegen
in `apps/desktop/packaging/msix`.

## Lizenzen

Ohne Lizenz laeuft die kostenlose Version. Freischaltungen: `basis`, `pro` (enthaelt Basis), `decoder` (Decoder-Datenbank), `club` (alles).
Ein Lizenzschluessel (`MOBA1-...`) ist mit ECDSA P-256 unterschrieben und wird in der App ohne Internet
mit dem oeffentlichen Schluessel in `ManufacturerKey.kt` geprueft. Eingabe: Burger-Menue, "Lizenz ...".

Den privaten Schluessel gibt es nur ausserhalb des Repositorys. Lizenzen erstellen:

```
./gradlew :tools:license:run --args="erstellen --privat /pfad/privat.key --name 'Max Mustermann' --module pro --bis 2027-12-31"
./gradlew :tools:license:run --args="pruefen MOBA1-..."
```

## Naechste Schritte

- s88-Zuordnung an der YD7001 pruefen; falls noetig LocoNet-Adapter
- Zentralen-Paket Stufe B (USB/seriell): Intellibox und Tams (P50X), Lenz LI-USB, LocoNet, Maerklin 6050
- Phase 2: Rueckmelder-Modul; gestellte Fahrstrassen im Stellpult hervorheben
