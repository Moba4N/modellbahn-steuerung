# Modellbahn-Steuerung

Modulare Steuerungssoftware fuer digitale Modellbahnen, fuer Android und Windows.
Das Architektur-Dokument beschreibt Module, Abo-Modell und Fahrplan der Entwicklung.

Stand: **Phase 0 (Fundament)**. Kern, Zentralen-API, Simulator, Sandbox und eine erste Oberflaeche.

## Aufbau

| Ordner | Inhalt |
| --- | --- |
| `core/model` | Datenmodell der Anlage: Loks, Weichen, Gleisabschnitte, s88-Kontakte |
| `core/station-api` | Einheitlicher Befehlssatz fuer jede Digitalzentrale (`CommandStation`) |
| `core/runtime` | Modul-Vertrag (`MobaModule`), Modul-Host, Ereignis-Bus, Lizenzpruefung |
| `core/ui-shell` | App-Rahmen mit Gleisspannung, Nothalt und Modulliste (Compose Multiplatform) |
| `adapters/simulator` | Virtuelle Zentrale ohne Hardware, inklusive s88-Belegtmelder |
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

## Naechste Schritte

- Phase 1: Z21-Adapter (getestet an YaMoRC YD7001), Modul "Fahren"
- Pruefen, ob die YD7001 s88-Rueckmeldungen ueber das Z21-Protokoll weitergibt; sonst LocoNet-Adapter
