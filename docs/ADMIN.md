# The Sift – Admin-Handbuch

**Benötigt:** Paper **26.3** (getestet mit Build 227) und Java 25. Das Datapack der Sift ist für genau
diese Minecraft-Version gebaut. Auf einer anderen Version lädt das Plugin die Sift nicht, deaktiviert
sich und schreibt beim Start, welche Version es erwartet (der Server selbst startet normal).

Stand: Phase 1, Schritte 1–4 und 8 (Dimension, Datapack, eigene Blöcke, Resource Pack) plus Item-Menü,
ab alpha.3 mit Weidenholz, Lotus, neuem Ichorglas und Ichorfarbe.
Wird mit jeder Phase ergänzt. Testablauf: `docs/TESTING.md`.

## Installation

1. `TheSift-<version>.jar` nach `plugins/` legen. Die Welt muss neu sein, damit die
   Sift-Generierung von Anfang an greift.
2. Server starten. Im Log stehen:
   - `[TheSift] Datapack installed to plugins/TheSift/datapack (… files)`
   - `Found new data pack TheSift/the_sift, loading it automatically`
   - `[TheSift] The Sift is loaded as world 'world_the_sift_the_sift' (the_sift:the_sift, …)`
3. Prüfen: `/datapack list` zeigt `TheSift/the_sift` als aktiv, `/sift tp` bringt dich in die Sift.
   Danach können Spieler die Sift betreten.

Hinweis zu 0.1.0-alpha.1: Eine von dieser Version angelegte Datei
`<welt>/dimensions/the_sift/the_sift/paper-world.yml` mit `block-updates`-Einträgen ist wirkungslos
und kann gelöscht werden.

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
- `resource-pack.required` (Standard `false`): Das Pack wird angeboten, Ablehnen ist erlaubt; ohne Pack
  sehen Sift-Blöcke wie Pilzblöcke aus und Namen erscheinen auf Deutsch (Fallback). Mit `true` wird
  getrennt, wer ablehnt (`kick-on-decline`).
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
| Brauner Pilzblock | Gesunder Sculk, Weidenstamm, -holz (entrindet und nicht, je Achse), -bretter, -laub |
| Roter Pilzblock | Trockener gesunder Sculk |
| Stolperdraht (`tripwire`) | Pflanzen, Lotus, hängende Wurzeln, Setzling, Sculkblume |
| Chorus-Pflanze | Ichorglas |

Wasser in der Sift hat die Biomfarbe `#9dc5ff` und sieht dadurch nach Ichor aus (das Datapack setzt sie
in allen Sift-Biomen). Weidenstämme richten sich beim Setzen nach der angeklickten Fläche aus und
lassen sich mit einer Axt entrinden. Weiden-Treppen, -Türen, -Zäune usw. bleiben Mangrovenholz,
Stein-Treppen/-Stufen/-Mauern Tiefenschiefer (Begründung: `docs/TEXTURE-COVERAGE.md`).

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

### Ichorglas aus alpha.1/alpha.2

Ab alpha.3 nutzt Ichorglas einen Chorus-Zustand mit Armen (fast volle Kollision). Bereits gesetztes
Glas behält seinen alten Zustand: es sieht weiter wie Ichorglas aus, hat aber die alte, kleine
Kollision. Abbauen (mit Behutsamkeit) und neu setzen ergibt die neue Form. `/sift debug shape <x> <y> <z>`
zeigt, welche Form ein Block hat.

### Bekannte Einschränkungen

- Schritt- und Abbaugeräusche bestimmt der Client nach dem Vanilla-Block: Siftschiefer, Sculk und
  Weidenlaub klingen nach Holz (Pilzblock), Pflanzen nach Faden, Ichorglas nach Chorus.
- Weidenholz und -laub brennen nicht, Weidenlaub zerfällt nicht; Rezepte (z. B. Bretter) folgen mit
  Schritt 5.
- Zwei Ichorglas-Blöcke nebeneinander blenden die Innenflächen nicht aus.
- Wer im End gezielt Chorus so baut, dass ein Stück oben und an allen vier Seiten Chorus hat, unten
  aber nicht, sieht dort mit dem Pack Ichorglas. Der Block bleibt Chorus; natürlich wächst das nicht.
- Explosionen werden vereinfacht: Sift-Blöcke ohne `explosion-proof` gehen bei jeder Explosion kaputt
  (keine Strahlstärke-Rechnung wie bei Vanilla).
- Seelenblock leuchtet noch nicht, Sift-Geröll fällt noch nicht (folgen mit der Weltgenerierung).
- In anderen Welten (mit Nachbar-Updates) können Spieler Pilzblöcke so aneinandersetzen, dass Vanilla
  zufällig einen der verwendeten Zustände erzeugt; mit dem Resource Pack sieht der Block dann wie ein
  Sift-Block aus. Er bleibt ein Pilzblock.

## Item-Menü und Rechte

- `/sift items` öffnet ein Truhen-Menü mit allen Sift-Items, sortiert nach Kategorien (Blöcke, Erze,
  Pflanzen/Holz, Werkzeuge, Rüstung, Materialien, Sonstiges). Unten: ◀/▶ blättern, Reiter wechseln
  die Kategorie. Klick = 1 Item, Shift-Klick = ganzer Stapel. Nichts kann ins Menü gelegt werden.
- `/sift give <spieler> <id> [anzahl]` mit Tab-Vervollständigung der Ids.
- Rechte (Standard jeweils op):
  - `thesift.admin`: alle `/sift`-Befehle, schließt `thesift.admin.items` ein.
  - `thesift.admin.items`: nur `/sift items` und `/sift give` (z. B. für Bauteam-Mitglieder).
- Die Items kommen aus derselben Registry wie Drops (und ab Schritt 5 Rezepte und Loot): Neue Items
  erscheinen automatisch im Menü. Werkzeuge und Rüstung folgen mit Schritt 5.

## Drops und Erze

- Sift-Geröll wie im Original (und wie Vanilla-Kies/Feuerstein): mit Behutsamkeit das Geröll, sonst
  mit 10 % (Glück I/II/III: 14 / 25 / 100 %) einen Ichorsplitter, sonst das Geröll selbst. Mehrmals
  hintereinander nur Geröll ist also normal. Die Chancen stehen in `blocks.sift_rubble.drops.chances`.
- Erzverteilung wie im Original (Weltgenerierung per Datapack):

  | Erz | Höhe | Adern pro Chunk | Größe |
  |---|---|---|---|
  | Siftiterz | y 0–15 | 1 | 8 |
  | Charoiterz | y 0–32 (Mitte häufiger) | 1 | 7 |
  | Diamanterz | y 0–31 | 2 | 9 |
  | Smaragderz | y 0–63 | 10 | 9 |
  | Steinkohle | y 0–127, zusätzlich an der Oberfläche | 20 + 10 | 17 |

- `/sift debug find <id> [radius]` sucht den nächsten Block (z. B. `siftslate_siftite_ore`) um dich
  herum und zeigt einen anklickbaren Teleport.

## Befehle (`thesift.admin`)

| Befehl | Wirkung |
|---|---|
| `/sift tp` | In die Sift bzw. zurück in die Hauptwelt |
| `/sift items` | Item-Menü (auch mit `thesift.admin.items`) |
| `/sift give <spieler> <id> [anzahl]` | Sift-Block oder -Item geben (auch mit `thesift.admin.items`) |
| `/sift pack info`, `reload`, `send <spieler>` | Resource Pack anzeigen, neu laden, erneut senden |
| `/sift debug item <id>` | Komponenten eines Sift-Items anzeigen |
| `/sift debug break <x> <y> <z> <werkzeug> [none\|silk\|fortune1-3] [anzahl]` | Abbauzeit, Drops und XP eines Sift-Blocks berechnen, mit Statistik über viele Abbauvorgänge |
| `/sift debug find <id> [radius]` | Nächsten Sift-Block dieser Id suchen |
| `/sift debug populator` | Zähler des Test-Populators |
| `/sift debug scan [radius] [chunkX chunkZ]` | Chunks der Sift laden/generieren und Sift-Blöcke zählen |
| `/sift debug shape <x> <y> <z>` | Kollisionsboxen eines Blocks in der Sift (Pixel, Anteil am Würfel) |
| `/sift debug chorus <radius> <chunkX> <chunkZ>` | End-Chunks laden und zählen, ob Chorus dort Zustände der Sift hat (sollte 0 sein) |

## Test-Populator

`debug.populator-marker` (Standard `false`) setzt in jedem neu generierten Sift-Chunk einen
Glowstone-Block auf die höchste Stelle der Chunkmitte. Nur für Tests, nie auf dem Produktivserver.
Er wird mit der echten Generierung (Phase 1, Schritt 6) entfernt.
