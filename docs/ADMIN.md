# The Sift – Admin-Handbuch

Stand: Phase 1, Schritte 1–4 und 8 (Dimension, Datapack, eigene Blöcke, Resource Pack). Wird mit jeder
Phase ergänzt.

## Installation und erster Start

1. `TheSift-<version>.jar` nach `plugins/` legen (Paper 26.3, Java 25). Die Welt muss neu sein,
   damit die Sift-Generierung von Anfang an greift.
2. Server starten. Im Log stehen:
   - `[TheSift] Datapack installed to plugins/TheSift/datapack (… files)`
   - `Found new data pack TheSift/the_sift, loading it automatically`
   - `[TheSift] The Sift is loaded as world 'world_the_sift_the_sift' (the_sift:the_sift, …)`
3. Prüfen: `/datapack list` zeigt `TheSift/the_sift` als aktiv, `/sift tp` bringt dich in die Sift.
4. Danach können Spieler die Sift betreten. **Ein Neustart nach dem ersten Start ist nicht nötig.**

Ältere Stände dieses Handbuchs verlangten einen Neustart, weil das Plugin eine `paper-world.yml`
für die Sift schrieb. Das war wirkungslos (siehe nächster Abschnitt) und ist entfernt. Eine von
älteren Versionen angelegte Datei `<welt>/dimensions/the_sift/the_sift/paper-world.yml` mit
`block-updates`-Einträgen schadet nicht und kann gelöscht werden.

## Resource Pack

Spieler brauchen das Resource Pack der Sift (Texturen, Modelle, Sounds, deutsche Texte). Das Plugin
schickt es beim Betreten des Servers.

- Jedes GitHub-Release (`v<version>`) enthält `TheSift-<version>.jar`, `the-sift-pack.zip` und
  `the-sift-pack.zip.sha1`. Plugin und Pack eines Releases gehören zusammen.
- `resource-pack.url` zeigt standardmäßig auf das Pack des Releases, das zur Plugin-Version passt
  (`{version}` wird ersetzt). Eigener Webspace: dort die ZIP hochladen und die URL eintragen.
- `resource-pack.sha1`: leer lassen, dann lädt das Plugin das Pack beim Start einmal herunter und
  berechnet die SHA-1 selbst (Log: `Resource pack … (SHA-1 …)`). Alternativ den Inhalt von
  `the-sift-pack.zip.sha1` eintragen; dann wird beim Start nichts heruntergeladen.
- `resource-pack.required: true`: Wer das Pack ablehnt, wird mit `kick-on-decline` getrennt.
- Nach Änderungen: `/sift pack reload`. `/sift pack info` zeigt URL und SHA-1, `/sift pack send <spieler>`
  schickt das Pack erneut.
- Das Pack ersetzt nur die Blockstates von Pilzblöcken, Stolperdraht und Chorus-Pflanze; deren normale
  Zustände sehen weiter wie in Vanilla aus. Andere Server-Packs bleiben aktiv (das Plugin ersetzt sie nicht).

Selbst bauen: `./gradlew :packgen:resourcePack` legt `packgen/build/pack/the-sift-pack.zip` und die
`.sha1` an.

## Wie eigene Blöcke funktionieren

Paper kennt keine neuen Blöcke. Die Blöcke der Sift nutzen Zustände von Vanilla-Blöcken, die im
normalen Spiel nicht vorkommen; das Resource Pack gibt ihnen das Aussehen aus der Original-Mod:

| Vanilla-Block | Sift-Blöcke |
|---|---|
| Pilzstiel (`mushroom_stem`) | Siftschiefer-Familie, Erze, Geröll, Seelenblock, Ichorschneeblock |
| Brauner/roter Pilzblock | Gesunder bzw. trockener Sculk |
| Stolperdraht (`tripwire`) | Pflanzen, hängende Wurzeln, Setzling, Sculkblume |
| Chorus-Pflanze | Ichorglas |

Die genaue Zuordnung steht in `pack-src/block-mapping.json`.

### Schutz vor Nachbar-Updates

Vanilla berechnet die Seiten von Pilzblöcken und die Verbindungen von Stolperdraht und
Chorus-Pflanzen neu, sobald sich ein Nachbar ändert. Dabei würde aus einem Sift-Block ein anderer.

- Paper hat dafür Schalter (`block-updates.disable-mushroom-block-updates` usw.). Sie stehen in
  **`paper-global.yml`** und gelten für **alle** Welten: Stolperdraht-Fallen und Chorus-Farmen
  funktionieren dann nirgends mehr. Das Plugin braucht sie nicht; **bitte nicht setzen**.
- Stattdessen schützt das Plugin die Sift-Blöcke selbst: Ändert sich ein Block neben einem
  Sift-Block, unterdrückt es das Form-Update und erledigt für die Vanilla-Nachbarn das Wichtige
  einen Tick später (Blöcke ohne Halt brechen ab, Wasser und Lava fließen nach).
- Mit `custom-blocks.place-only-in-sift: true` (Standard) gilt das nur in der Sift.

Nebenwirkungen in der Sift, direkt neben Sift-Blöcken: Zäune, Mauern, Glasscheiben und Treppen
verbinden sich dort nicht neu; Redstone funktioniert normal.

### Befehle und Werkzeuge, die Blöcke setzen

`/setblock` und `/fill` berechnen Blockformen neu und verwandeln damit Sift-Blöcke. Für Sift-Zustände
immer den Modus `strict` verwenden, z. B.
`/execute in the_sift:the_sift run setblock 0 100 0 minecraft:mushroom_stem[…] strict`.
Einfacher ist `/sift give` und Setzen von Hand. Für WorldEdit & Co. gilt: Platzieren ohne
Nachbar-Updates (Standard bei WorldEdit) erhält die Zustände.

### Konfiguration (`config.yml`)

- `custom-blocks.place-only-in-sift` (Standard `true`): Sift-Blöcke lassen sich nur in der Sift setzen.
- `custom-blocks.outside-sift-fallback`: `deny` (Hinweis, nichts passiert) oder `vanilla`
  (setzt stattdessen `blocks.<id>.fallback`, z. B. Tiefenschiefer für Siftschiefer).
- `blocks.<id>`: Härte, Werkzeug, Mindeststufe, Drops, Erfahrung, Explosionsfestigkeit,
  Untergrund für Pflanzen. Die Standardwerte entsprechen dem Original (Siftschiefer wie
  Tiefenschiefer, Erze wie Tiefenschiefer-Erze, Siftiterz braucht eine Diamantspitzhacke).
- Neue Einstellungen späterer Versionen werden beim Start automatisch in die bestehende
  `config.yml` übernommen.

### Abbauen

Das Plugin passt beim Abbauen die Abbaugeschwindigkeit an (Attribut `block_break_speed`), damit ein
Sift-Block so lange braucht wie im Original. Drops, Glück, Behutsamkeit und Erfahrung kommen aus
`blocks.<id>.drops`.

### Bekannte Einschränkungen

- Schritt- und Abbaugeräusche bestimmt der Client nach dem Vanilla-Block: Siftschiefer und Sculk
  klingen nach Holz (Pilzblock), Pflanzen nach Faden.
- Explosionen werden vereinfacht: Sift-Blöcke ohne `explosion-proof` gehen bei jeder Explosion kaputt
  (keine Strahlstärke-Rechnung wie bei Vanilla).
- Seelenblock leuchtet noch nicht, Sift-Geröll fällt noch nicht (folgen mit der Weltgenerierung).
- In anderen Welten (mit Nachbar-Updates) können Spieler Pilzblöcke so aneinandersetzen, dass Vanilla
  zufällig einen der verwendeten Zustände erzeugt; mit dem Resource Pack sieht der Block dann wie ein
  Sift-Block aus. Er bleibt ein Pilzblock.

## Befehle (`thesift.admin`)

| Befehl | Wirkung |
|---|---|
| `/sift tp` | In die Sift bzw. zurück in die Hauptwelt |
| `/sift give <spieler> <id> [anzahl]` | Sift-Block oder -Item geben |
| `/sift pack info`, `reload`, `send <spieler>` | Resource Pack anzeigen, neu laden, erneut senden |
| `/sift debug item <id>` | Komponenten eines Sift-Items anzeigen |
| `/sift debug break <x> <y> <z> <werkzeug>` | Abbauzeit, Drops und XP eines Sift-Blocks in der Sift berechnen |
| `/sift debug populator` | Zähler des Test-Populators |
| `/sift debug scan [radius] [chunkX chunkZ]` | Chunks der Sift laden/generieren und Sift-Blöcke zählen |

## Test-Populator

`debug.populator-marker` (Standard `false`) setzt in jedem neu generierten Sift-Chunk einen
Glowstone-Block auf die höchste Stelle der Chunkmitte. Nur für Tests, nie auf dem Produktivserver.
Er wird mit der echten Generierung (Phase 1, Schritt 6) entfernt.
