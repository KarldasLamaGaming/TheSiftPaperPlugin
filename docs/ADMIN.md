# The Sift – Admin-Handbuch

**Benötigt:** Paper **26.3** (getestet mit Build 227) und Java 25. Das Datapack der Sift ist für genau
diese Minecraft-Version gebaut. Auf einer anderen Version lädt das Plugin die Sift nicht, deaktiviert
sich und schreibt beim Start, welche Version es erwartet (der Server selbst startet normal).

Stand: Phase 1, Schritte 1–6 und 8 (Dimension, Datapack, eigene Blöcke, Items und Rezepte,
Weltgenerierung mit Haupt-Portal, Resource Pack) plus Item-Menü. alpha.3: Weidenholz, Lotus, Ichorglas,
Ichorfarbe; alpha.4: Siftit-Ausrüstung, Rezepte, Loot, Advancements; alpha.5: Weidenbäume, Pflanzenflecken,
Oberflächen-Sculk, verlassene Portale, Haupt-Portal, brennbares Weidenholz; alpha.6: verlassene Portale
wie im Original 1.1.1, Protokoll und `/sift debug portals`; Schritt 7a: Portal-Mechanik (Durchgang, Konsolen,
Melodie, `/sift portal`); Schritt 7b: Portal-Schreine in Hauptwelt, Nether und End, Schlüssel,
Sift-Kompass.

## Installation

1. `TheSift-<version>.jar` nach `plugins/` legen. Die Welt muss neu sein, damit die
   Sift-Generierung von Anfang an greift.
2. Server starten. Im Log stehen:
   - `[TheSift] Datapack installed to plugins/TheSift/datapack (… files)`
   - `Found new data pack TheSift/the_sift, loading it automatically`
   - `[TheSift] The Sift is loaded as world 'world_the_sift_the_sift' (the_sift:the_sift, …)`
3. Prüfen: `/datapack list` zeigt `TheSift/the_sift` als aktiv. Beim ersten Start baut das Plugin das
   Haupt-Portal der Sift (Log: `Selecting a location for the main Sift portal (generating 441 chunks once)`,
   kurz danach `Main Sift portal ready at x y z`; dauert je nach Server ein bis wenige Minuten).
   `/sift tp` bringt dich danach direkt ans Haupt-Portal.
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

Alle Abweichungen vom Original, die bewusst so bleiben, sammelt `docs/ABWEICHUNGEN.md`.

- Schritt- und Abbaugeräusche bestimmt der Client nach dem Vanilla-Block: Siftschiefer, Sculk und
  Weidenlaub klingen nach Holz (Pilzblock), Pflanzen nach Faden, Ichorglas nach Chorus.
- Weidenholz und -laub brennen nicht, Weidenlaub zerfällt nicht und taugt nicht als Brennstoff.
- Zwei Ichorglas-Blöcke nebeneinander blenden die Innenflächen nicht aus.
- Wer im End gezielt Chorus so baut, dass ein Stück oben und an allen vier Seiten Chorus hat, unten
  aber nicht, sieht dort mit dem Pack Ichorglas. Der Block bleibt Chorus; natürlich wächst das nicht.
- Explosionen werden vereinfacht: Sift-Blöcke ohne `explosion-proof` gehen bei jeder Explosion kaputt
  (keine Strahlstärke-Rechnung wie bei Vanilla).
- Seelenblock leuchtet noch nicht, Sift-Geröll fällt noch nicht (folgen später).
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
- Die Items kommen aus derselben Registry wie Drops, Rezepte und Loot: Neue Items erscheinen automatisch
  im Menü.

## Items, Rezepte, Siftit

### Siftit-Ausrüstung

Siftit-Werkzeuge, -Waffen und -Rüstung bauen auf den Netherit-Items auf und übernehmen alles, was gleich
bleibt (Rüstungspunkte, Angriffstempo, Feuerfestigkeit, Verzauberbarkeit, Rüstungsbesätze). Geändert
werden nur die Werte aus `config.yml` → `items.siftite`:

| Wert | Siftit (Standard) | Netherit |
|---|---|---|
| Haltbarkeit Werkzeuge/Waffen (`tools.durability`) | 2200 | 2031 |
| Abbautempo (`tools.mining-speed`) | 9,5 | 9 |
| Schadensbonus (`tools.attack-damage-bonus`, Schwert 8,5 statt 8) | 4,5 | 4 |
| Rüstungs-Haltbarkeit (`armor.durability-multiplier`; Helm/Brust/Beine/Stiefel) | 39 (429/624/585/507) | 37 (407/592/555/481) |
| Härte je Teil (`armor.toughness`) | 3 | 3 |
| Rückstoßresistenz je Teil (`armor.knockback-resistance`) | 0,12 | 0,1 |

Weg dorthin wie im Original: Siftiterz → Siftitklumpen → 9 Klumpen = Siftitbarren. Am Schmiedetisch
ergibt Aufwertungsvorlage + Netherit-Teil + Siftitbarren das Siftit-Teil (Verzauberungen, Name und
Besätze bleiben). Die Vorlage liegt in Truhen verlassener Portale (Schritt 6) und lässt sich mit
7 Diamanten und 1 Siftschiefer verdoppeln. Repariert wird am Amboss mit Siftitbarren.

Werte gelten für Items, die nach dem Neustart entstehen; schon vorhandene Items behalten ihre Werte.

### Schutz: Sift-Items sind keine Vanilla-Items

Alle Sift-Items werden nie wie das Vanilla-Item behandelt, auf dem sie aufbauen: Siftit-Ausrüstung, alle
Sift-Materialien (Charoit, Ichorscherbe, Siftitklumpen und -barren, Vorlage), Samen, Ichorschneeball,
Essen, Ichorflasche und Schallplatten (in `items.yml` alle mit `protect: true`; `ItemRegistryTest` prüft,
dass keines fehlt) und seit alpha.6 auch alle Block-Items (Pflanzen auf Faden-Basis, Ichorglas auf
Glas-Basis, Siftschiefer, Weidenholz usw. auf Pilzblock-Basis). Charoit gibt also kein Buch, Überwucherte
Wedel geben keinen Bogen, Ichorglas keine Glasscheiben oder Leuchtfeuer. Die Sift-Rezepte (Namensraum
`the_sift`, z. B. Weidenbretter → Stöcke/Werkbank, Weidenstamm → Holzkohle, 6 Ichorglas →
Ichorglasscheibe, Siftschiefer an der Steinsäge, seit alpha.24 auch 42 Holzrezepte mit Weidenbrettern
oder -stämmen wie Truhe, Betten und Holzwerkzeuge, siehe ABWEICHUNGEN.md) nehmen sie weiter an:

- Werkbank und Crafter: Vanilla-Rezepte nehmen sie nicht an (z. B. kein Reparieren durch Kombinieren).
- Schmiedetisch: keine Vanilla-Aufwertung mit Siftit-Vorlage, -Barren oder -Teil; Rüstungsbesätze auf
  Siftit-Rüstung gehen.
- Amboss: Siftit-Teile nur mit Siftitbarren reparieren oder mit demselben Siftit-Teil kombinieren;
  Verzauberungsbücher und Umbenennen gehen. Netherit-Teile lassen sich nicht mit Siftit reparieren.
- Schleifstein: zwei Teile nur, wenn beide dasselbe Siftit-Teil sind.
- Steinsäge: nur die Sift-Rezepte (Siftschiefer → Treppen, Stufen, Mauern, Ziegel …).
- Webstuhl, Kartentisch, Dorfbewohner-Handel: geschützte Items werden nicht angenommen.
- Ofen-Brennstoff ist davon nicht betroffen (Weidenholz brennt weiter, siehe Feuer und Brennstoff).

Echtes Netherit und alle Vanilla-Rezepte bleiben unverändert (belegt durch `SiftiteGuardRulesTest` und
`/sift debug selftest`).

### Rezepte

Das Plugin registriert die 76 Rezepte des Originals (Werkbank, Ofen/Räucherofen/Lagerfeuer, Steinsäge,
Schmiedetisch) beim Start; sie stehen im Rezeptbuch (`recipes.discover-on-join`). Sift-Zutaten werden an
ihrer Sift-Id erkannt (auch umbenannt); Vanilla-Zutaten nur als echte Vanilla-Items.

- Ergebnisse, die es hier nur als Vanilla-Block gibt, ergeben diesen: Siftschiefer-Treppen/-Stufen/-Mauern
  → Tiefenschiefer-Varianten, Weiden-Tür/-Zaun/-Schild/-Boot usw. → Mangrove, Ichorglasscheibe → lila
  Glasscheibe, Ichorschnee → Schnee.
- 3 Rezepte fehlen, 3 sind hinzugekommen: siehe `docs/ABWEICHUNGEN.md`.

### Weitere Items

| Item | Werte (`config.yml` → `items`) |
|---|---|
| Rohes/Gebratenes Sifterfleisch | `food`: 3/1,8 und 8/12,8 (wie Schweinefleisch); Quelle: Sifter (Phase 2) |
| Ichorflasche | `drinks`: Farbe, 3 s Regeneration; Füllen an Ichor oder Kessel in der Sift (ab alpha.21, siehe „Ichor“) |
| Schallplatten Rift, Wellspring | `music-discs`: Lied aus dem Datapack; Rift liegt in Truhen verlassener Portale |

### Loot und Advancements

- Truhen-Loot verlassener Portale (`the_sift:chests/abandoned_portal`) kommt mit dem Datapack; das Plugin
  ersetzt die Sift-Items beim Erzeugen der Beute durch die echten Items.
- Die 22 Advancements des Originals sind im Datapack. Automatisch: Sift betreten, Biome erkunden, Siftit-
  Rüstung, Sculkblumensamen, antike Stätte. Vom Plugin vergeben: Charoiterz und Siftiterz abbauen, Holz in
  der Sift anzünden. Die übrigen (Mobs, Riss, Ichor, Charoit-Verzaubern; Siftit-Erhalt seit alpha.23) kommen mit ihren
  Funktionen.

## Weltgenerierung

Das Datapack erzeugt Gelände, Biome, Erze und die einfachen Features des Originals. Die Features, die
im Original Java-Code sind, setzt das Plugin beim Generieren neuer Chunks (`config.yml` → `worldgen`,
Neustart nötig, gilt nur für neue Chunks):

| Feature | Wo (Biome) | Häufigkeit (Standard wie Original) |
|---|---|---|
| Überwucherte Weiden (klein/groß, 38 % groß) | Wald, Waldhänge, Gipfel dicht; Lichtung, Hänge, Ichorschnee-Gipfel vereinzelt | dicht 5 Versuche je Chunk, vereinzelt jeder 7. Chunk |
| Blumenflecken (Lotus, Sonnenstrahl, Flüsterblüte) | alle außer Ödland; Ödland seltener | jeder 9. Chunk, Ödland jeder 7. |
| Pflanzenflecken auf Siftschiefer | alle | jeder 10. Chunk |
| Oberflächen-Sculk (Vanilla-Sculk mit Sensoren, Kreischern, die Wärter rufen können, Katalysatoren) | alle außer Ichorschnee-Gipfel und Tiefes Dunkel | höchstens eine Region (50–120 Blöcke) je 512 × 512 |
| Verlassene Portale mit Truhe (`the_sift:chests/abandoned_portal`) | alle außer Tiefes Dunkel | ein Kandidat je 20 × 20 Chunks (im mittleren Drittel der Zelle), davon baut der Anteil `chance` (Standard 1.0, siehe unten), nur auf passendem Gelände |
| Seelenschluchten (ab alpha.18): mäandernder Graben, bis 20 Blöcke tief, Seelenblöcke im Boden, aufsteigende Seelen-Partikel | alle | höchstens eine je 12 × 12 Chunks (Zellen um 6 Chunks versetzt, mittleres Drittel), nur auf passendem Gelände, mindestens 34 Blöcke vom Haupt-Portal |

Weiden, Lotus, Sonnenstrahlpflanze usw. entstehen aus den Vorlagen und Werten des Originals; Abweichungen
in `docs/ABWEICHUNGEN.md`.

### Ichor-Seen und -Quellen (ab alpha.25)

Wie im Original 1.1.1 (Werte in `worldgen.ichor-lakes.*` und `worldgen.ichor-springs.*`):
- **Seen:** In 1 von 3 Chunks der Wald-, Gipfel- und Ödland-Biome (Lichtung und Hänge: 1 von 2) wird ein See
  versucht, nicht im Tiefen Dunkel.
  - Die Variante hängt vom Gelände ab: SMALL (Radius 4–7) bis MEGA (Radius 14–16).
  - Es wird nur gebaut, wenn das Gelände flach genug ist, ein Strand passt und kein Ichor in 15–20 Blöcken liegt.
  - Becken aus Sculk oder Siftschiefer, Ufer je Biom, Felsformationen in großen Seen, kleine Pflanzen am Ufer.
  - Ab alpha.25.1: In sehr hügeligem Gelände versucht das Plugin wie das Original in 82 % der Fälle eine
    **Kaskade**. Sie besteht aus einem unteren See, einem Teich auf einem Bergsims und einem Abfluss mit fallendem
    Ichor; meist ist das Gelände dafür doch zu uneben. Große überwucherte Seen bekommen zu 27 % eine
    **Weideninsel**.
- **Quellen:** 16 Versuche je Chunk zwischen y 15 und 110, mindestens 12 Blöcke unter der Oberfläche, nur in
  einer Felswand mit genau einer Öffnung. Daraus fließt Ichor in Höhlen.
- **Leuchten:** Lichtblöcke Stufe 12 (`light-spacing`, Standard jeder 3. Block der Oberfläche).
  - Seit alpha.36 sitzen sie in der Luft direkt über dem Ichor, bei Quellen über der Öffnung in der Felswand
    (`ichor-springs.light`); die Quelle selbst ist ein normaler Ichor-Quellblock.
  - Bis alpha.35 waren sie wassergeloggt und saßen im Ichor. Das waren versteckte, dauerhafte Quellen (siehe
    ABWEICHUNGEN). In alten Chunks gilt weiter: Nimmt ein Spieler das Ichor eines solchen Blocks mit dem Eimer
    oder saugt ein Schwamm es auf, wird der Lichtblock zu Luft.
  - `/sift debug ichor scan [chunks]` (Standard 4, höchstens 8 Chunks Radius, nur geladene Chunks, in der Sift)
    zählt wassergeloggte Lichtblöcke (versteckte Quellen aus alten Chunks) und die Leuchtlichter über dem Ichor,
    mit Teleport zur ersten versteckten Quelle. Nach dem Welt-Reset muss dort überall 0 stehen.
  - Andere Lichtblöcke (Risse Stufe 15, Golems Stufe 14, eigene) werden nicht angefasst.
- **Finden:** `/sift debug ichor` zeigt die nächsten Seen und Quellen mit Koordinaten und Teleport-Klick (in
  der Sift: zu dir, sonst zu 0/0). `/sift debug worldgen` zeigt Zahlen, Gründe für nicht gebaute Seen und
  die Generierungszeit.
- **Zeitbudget** (Smoke-Test, 256 neue Chunks): im Mittel 1,0 ms je See-Kandidat (höchstens 18 ms) und
  0,1 ms je Chunk für Quellen.
- `/sift debug worldgen` zählt nicht gebaute Seen je Variante (SMALL … MEGA, CASCADE) mit Grund, dazu die Weideninseln.

### Ichor-Schnee (ab alpha.26)

`worldgen.ichor-snow.enabled` (Standard an), `willow-crowns`: In den Ichor-Schneegipfeln bekommt jede Spalte mit
natürlichem Boden 1–2 Schichten Ichor-Schnee (Vanilla-Schnee). Kleine Pflanzen werden ersetzt, auch Weidenkronen
werden verschneit, Siftschiefer-Bewuchs darunter wird verschneit. `/sift debug worldgen` zeigt die Schichten.
`ichor.false-alarm` schaltet das Advancement „False Alarm“ (Ichor-Schneeball in 16 Blöcken eines Wärters).

### Monolithen und Bögen (ab alpha.27)

- `worldgen.monoliths.enabled` (Standard an), einzeln `lush.enabled` / `bare.enabled`. Je Variante und
  Biomgruppe `rarity` (1 von N Chunks) und `biomes`, Werte des Originals: üppig 1/11 (überwuchert) bzw. 1/8
  (Ödland, Hänge, Schneegipfel), kahl 1/20 bzw. 1/15. `portal-clearance` (22) und `reserve-radius` (22,
  reservierte Fläche gegen Seen, Portale und andere Monolithen).
- `worldgen.arches.enabled`, `rarity` (5), `biomes` (alle Sift-Biome außer der Tiefen Dunkelheit),
  `portal-clearance` (23). Gebaut wird nur, wo eine Achse durch die Chunk-Mitte eine Senke findet, daher selten.
- `/sift debug landmarks` zeigt die je drei nächsten Monolithen und Bögen mit Teleport-Klick (auf die Spitze bzw.
  den Scheitel). Gespeichert im Welt-PDC der Sift (`the_sift:lush_monoliths`, `bare_monoliths`, `arches`).
  `/sift debug worldgen` zählt gebaute und nicht gebaute (mit Grund) und die Zeit je Kandidat.
- **Zeitbudget** (Smoke-Test, 256 neue Chunks): 2,1 ms je Monolith-Kandidat im Mittel (höchstens 28 ms),
  0,3 ms je Bogen-Kandidat (höchstens 30 ms), zusammen etwa 0,7 ms je Chunk.

### Sculk-Spitzen, Bewuchs-Aufräumen, Lavaboden (ab alpha.30)

- `worldgen.dry-sculk-spikes.enabled`, `overgrown`/`wastes` je mit `rarity` und `biomes` (Original 1/21 bzw.
  1/16), `portal-clearance` (18), `reserve-radius` (18). `/sift debug landmarks` zeigt die drei nächsten mit
  Teleport (Welt-PDC `the_sift:dry_sculk_spikes`).
- `worldgen.covered-growth-cleanup.enabled`, `depth` (16, Original 56): in jedem neuen Chunk zuletzt, von `depth`
  Blöcken unter der Oberfläche bis zu ihr: bedeckter Bewuchs wird kahl, Pflanzen über Ichor werden entfernt.
  Kostet etwa 2,5 ms je neuem Chunk (56: etwa 8 ms).
- `worldgen.lava-floor.enabled`, `max-y` (11): Luft bis zu dieser Höhe wird Lava.
- `/sift debug worldgen` zählt Spitzen (mit Gründen und Zeit) sowie aufgeräumte Blöcke und Lava.

### Spieler-Reset für den Go-live

`/sift admin reset-players` (Recht `thesift.admin.reset`, nicht in `thesift.admin` enthalten; nur mit
`admin.reset-players.enabled: true`) setzt alle Spieler für die Sift auf null: alle `the_sift:`-Daten im Spieler
(Intro, Siftit-Sicherungen, Rückkehrpunkte), alle `the_sift:`-Advancements, alle Sift-Gegenstände in Inventar,
Endertruhe und Shulkerkisten darin. Bestätigung mit `/sift admin reset-players confirm` innerhalb von 30 s.
Online-Spieler sofort, Offline-Spieler beim nächsten Login (solange der Schalter an ist). Ablauf: `docs/GOLIVE.md`.

### Wetter und Ichor-Schneefall (ab alpha.32)

- Die Sift hat ihr eigenes Wetter (Paper je Welt); `/weather` in der Sift gilt nur dort.
- `weather.ichor-snowfall.{enabled, temperature, max-layers}`: Schnee nur in `ichor_snowy_peaks`, dort in jeder
  Höhe; das Plugin setzt dafür beim Start die Temperatur des Bioms im installierten Datapack (Log `Ichor snowfall:
  temperature of ichor_snowy_peaks set to 0.0`, wirkt nach einem Neustart). Nur bei Blocklicht unter 10,
  höchstens 2 Schichten (mehr erst mit der Gamerule `snow_accumulation_height`).
- `weather.ichor-freezes` (false): Ichor (Wasser in der Sift) friert nicht zu.
- `/sift debug weather` zeigt das Wetter beider Welten und die Zähler (Schnee gebildet, abgelehnt je Biom,
  Eis abgelehnt).

### Himmel und Atmosphäre (ab alpha.29)

- `atmosphere.clouds` (Standard `false`): Wolken in der Sift. Beim Start setzt das Plugin im installierten
  Datapack die Wolkenfarbe auf durchsichtig (Log `Clouds of the Sift hidden (2 files)`); wirkt nach einem
  Neustart. `true` zeigt die Wolken immer.
- `atmosphere.intro-sound.{enabled, volume, delay-ticks}`: Intro-Klang einmal je Spieler beim ersten Aufenthalt
  in der Sift (Spieler-PDC `the_sift:intro_played`). `/sift debug intro` zeigt den Status, `reset` lässt ihn
  erneut spielen, `play` spielt ihn sofort.
- Was ohne Client-Mod nicht geht (Polarlicht-Himmel, Wolken nur bei Regen, Ichor-Wettertexturen,
  Ladebildschirm): ABWEICHUNGEN.md → „Himmel und Atmosphäre“.

### Schnüffler-Höhlen (ab alpha.28)

- `worldgen.sniffer-caves.enabled` (Standard an). `overgrown` und `wastes` je mit `cell-chunks`, `candidates`
  und `biomes` (Original: überwuchert 16 Chunks mit 4 Kandidaten je Zelle, Ödland/Hänge/Gipfel/Schneegipfel 28
  mit 3). `portal-clearance` (12), `family` (Schnüffler-Familie im Nest), `ichor-light` (Becken leuchtet).
- `worldgen.sniffer-plant-patches.enabled`, `rarity` (14), `biomes`.
- Die Familie wird beim Generieren vorgemerkt und auf dem Main-Thread erzeugt, sobald die Nest-Chunks geladen
  sind (höchstens 4 je Tick, 80 Versuche); vorgemerkte Schnüffler gehen bei einem Neustart verloren.
- `/sift debug landmarks` zeigt die drei nächsten Höhlen mit **[Nest]**- und **[Eingang]**-Teleport (Welt-PDC
  `the_sift:sniffer_caves`), `/sift debug worldgen` Kandidaten, Gründe, Zeit, Eier, Pflanzen und Schnüffler.
- Fackellilien, Kannenpflanzen und Blumen bleiben auf Sift-Boden stehen (wie im Original).

**Alte Chunks (#16):** Neue Weltgenerierungs-Features (Seen, Quellen, Monolithen, Bögen …) entstehen nur
in Chunks, die nach dem Update **neu generiert** werden. Schon erkundete Gebiete bekommen sie nicht.
Vor dem Produktionsstart die Sift zurücksetzen (siehe TESTING.md → „Sift-Welt zurücksetzen“), danach nur
noch neue Gebiete erkunden.

### Seelenschluchten

`worldgen.soul-canyons` (Standard an, `[Start]`, nur neue Chunks): `cell-chunks` (12), `portal-clearance` (34),
`min-souls` (8, weniger Seelenblöcke = keine Schlucht), `biomes`, `particles` (siehe unten). Jede Schlucht hinterlässt
Markierungen im Welt-Speicher der Sift (Eingang, Mitte, Zentrum der Seelen, jeder Seelenblock, „Golem
ausstehend“ für den Echo Golem ab alpha.19). Von Spielern gesetzte oder abgebaute Seelenblöcke werden ein- bzw.
ausgetragen.
- **Seelen-Partikel** (`worldgen.soul-canyons.particles`, ab alpha.18.1, `[Start]`):

  | Wert | Standard | Bedeutung |
  |---|---|---|
  | `enabled` | `true` | an/aus |
  | `type` | `SCULK_SOUL` | Partikelart ohne Zusatzdaten (z. B. `SOUL`, klein wie im Original) |
  | `amount` | 3 | Partikel je Seelenblock und Lauf |
  | `souls-per-run` | 4 | so viele offene Seelenblöcke je Schlucht bekommen pro Lauf Partikel |
  | `air-above` | 4 | so viele freie Blöcke (Luft, Pflanzen) über dem Seelenblock, damit er als offen gilt (Original 50) |
  | `view-distance` | 48 | nur Schluchten mit einem Spieler in diesem Abstand laufen, und nur diese Spieler bekommen die Partikel |
  | `interval-ticks` | 10 | alle N Ticks ein Lauf |

  Die alte Form `particles: true` bzw. `false` geht weiter (dann gelten die Standardwerte). Es laufen nur
  Schluchten mit Spieler in Sichtweite und geladenem Chunk; die offenen Seelenblöcke werden 5 s zwischengespeichert.
- `/sift debug landmarks` nennt die Zahl der Schluchten und die nächste mit Teleport-Klick zum Eingang, dazu eine
  Zeile „Seelen-Partikel“: Schluchten in Sichtweite der eigenen Position, ihre Seelenblöcke, davon offen, und was der
  letzte Lauf gesendet hat.
- `/sift debug worldgen` zeigt gebaute Schluchten, Gründe für nicht gebaute und die Zeit je Kandidat.
- `/sift debug canyon build` baut **in schon generiertem, geladenem Gelände** eine Schlucht mit demselben Plan
  und trägt ihre Markierungen ein (zum Testen ohne neue Chunks). Passt das Gelände an der eigenen Position
  nicht, probiert der Befehl bis zu 24 weitere Mittelpunkte im 16er-Raster (bis 32 Blöcke weit). Am besten
  auf flachem, kahlem Siftschiefer; Bäume, Ichor und Bauten verhindern die Schlucht. Der Befehl verändert
  dauerhaft Blöcke, also nur auf dem Testserver.

### Verlassene Portale finden

Verlassene Portale bestehen nur aus Siftschiefer, Sculk und einer Truhe und sehen deshalb fast wie
Gelände aus. Das Plugin protokolliert seit alpha.6 jeden Kandidaten im Weltspeicher der Sift
(`the_sift:abandoned_portal_log`):

- `/sift debug portals` listet alle gebauten Portale (Klick auf die Koordinaten teleportiert zur Truhe)
  und alle abgelehnten Kandidaten mit Position und Grund, z. B. „Gelände zu steil (Höhenunterschied 25 >
  18)“, „Platz belegt durch the_sift:…“, „kein Sift-Boden in der Chunkmitte (verdeckt durch …)“,
  „Fundament: Boden 10 Blöcke vom Vorlagenboden entfernt (> 8)“ oder „zu nah am Haupt-Portal“. Das Plugin
  probiert alle vier Drehungen; passt keine, stehen ihre Gründe hintereinander.
- `/sift debug landmarks [x z [zellen]]` zeigt die Kandidaten um eine Position mit Status: gebaut,
  abgelehnt (Grund), noch nicht generiert oder „generiert vor alpha.6, kein Protokoll“.

Chunks, die vor alpha.6 generiert wurden, stehen nicht im Protokoll.

`worldgen.abandoned-portals.chance` (0.0–1.0, Standard seit alpha.28 **1.0** wie im Original, vorher 0.25)
legt fest, welcher Anteil der Kandidaten überhaupt bauen darf; die Auswahl hängt nur von Seed und Chunk ab.
Seit Seelenschluchten, Seen, Monolithen und Höhlen wie im Original Plätze belegen, ergibt 1.0 dieselbe Menge
wie das Original (Seed `lamakingdom`, gleiche 52 Kandidaten außerhalb der Startsperre: je 8 gebaut).
**Update auf alpha.28:** Eine vorhandene `config.yml` behält `chance: 0.25`; dort von Hand auf `1.0` setzen.
Nicht ausgewählte Kandidaten (nur bei `chance` unter 1.0) stehen in `/sift debug portals` als „nicht
ausgewählt (chance …)“.

**Update auf alpha.6:** Eine vorhandene `config.yml` behält `worldgen.abandoned-portals.cell-chunks: 32`
und hat noch kein `chance`; für die Verteilung des Originals `cell-chunks: 20` und `chance: 0.25` setzen
(fehlt `chance`, gilt 0.25). Das neue Raster gilt nur für neue Chunks; zum Testen
am besten eine frische Sift-Welt.

### Haupt-Portal

Beim ersten Start mit der Sift sucht das Plugin in der Nähe von 0/0 (±96 Blöcke) die flachste Stelle,
gleicht das Gelände an (Fundament, Klippen an beiden Enden, niedriger Vorplatz vor den Öffnungen) und
setzt das Haupt-Portal des Originals. Der Ankunftspunkt steht im Weltspeicher der Sift
(`the_sift:portal_anchor`); `/sift tp` nutzt ihn. Das Portal wird nur einmal gebaut, auch nach Neustarts.
Weiden halten danach 12 Blöcke, verlassene Portale 28 Blöcke Abstand vom Portal (plus 15 Blöcke Rand).
Die Öffnung des Haupt-Portals (20 × 6) ist der Ausgang der Sift und immer offen, siehe Portale.

`worldgen.main-portal.enabled: false` schaltet das Bauen ab (dann gibt es keinen Ankunftspunkt).

## Portale (Schritt 7a)

Wie im Original führt ein offenes Portal aus der Hauptwelt (auch aus Nether und End) zum Haupt-Portal der
Sift. Wer durch dessen Öffnung geht, kommt dorthin zurück, wo er eingestiegen ist; ohne gespeicherten
Rückkehrpunkt an den Spawn der Hauptwelt. Das gilt für alle Entities (Spieler, Tiere, Items), nicht für
Reiter samt Reittier. Nach einer Reise muss man die Portalfläche erst verlassen, bevor sie wieder wirkt,
auch nach einem Neustart oder Relog.

**Aufbau eines Portals:**
- **Rahmen:** Verstärkter Tiefenschiefer (Vanilla), aufrecht, 3–23 Blöcke je Seite, innen frei.
- **Konsolen:** Klingender Tiefenschiefer (`sonorous_deepslate`) im Umkreis von `portals.frame-radius`
  (10) um den Rahmen.
  - Im Horn-Modus schlafen die Konsolen; ein Notenblock darauf klingt nur verzerrt.
  - Im Noten-Modus hat ein Notenblock darauf die 8 Klänge der Sift statt der Vanilla-Töne:
    Rechtsklick schaltet den Klang weiter (1 → 2 → … → 8 → 1), Schlagen spielt ihn; die Actionbar zeigt „Klang N von 8“.
- **Öffnen (seit alpha.37 nur mit dem Schlüssel):** Rechtsklick mit dem passenden Schlüssel auf eine Konsole
  (oder ihren Notenblock). Die Konsolen erwachen nacheinander mit Farbstoß, der Auslöse-Klang ertönt, dann
  spielen die Konsolen ihre Klänge mit Lichtstrahlen nach und die Fläche füllt sich vom Rand zur Mitte
  (zusammen etwa 15 s). Eine Melodie gibt es nicht mehr; Admins: `/sift portal activate` (derselbe Ablauf).
- **Offen** bleibt das Portal dauerhaft, nur `/sift portal deactivate` oder `lock` schließt es.
- Die Fläche besteht aus unsichtbaren Lichtblöcken (Stufe `portals.light-level`, 12) und einer
  Nebelfläche (Item-Display).

Portale gibt es nur dort, wo sie registriert sind: in den Portal-Schreinen (unten) oder per Befehl. Pro
Welt stehen sie im Weltspeicher (`the_sift:portals`).

### Schlüssel öffnen die Portale (ab alpha.37)

Bis alpha.36 weckte der Schlüssel (oder der Singer) die Konsolen, und eine Melodie öffnete das Portal. Seit
alpha.37 öffnet allein der passende Schlüssel, mit demselben Ablauf (Wecken, Auslöse-Klang, Nachspielen,
Fläche). Gründe stehen in ABWEICHUNGEN.md.

```yaml
portals:
  keys:
    enabled: true     # false = nur Admins öffnen Portale (/sift portal activate)
    consume: true     # Schlüssel beim Öffnen verbraucht (nie im Kreativmodus)
    overworld:    {item: echo_resonator,    loot: {tables: [...], chance: 0.03}}
    nether:       {item: soul_ember_core,   loot: {tables: [...], chance: 0.03}}
    end:          {item: void_resonator,    loot: {tables: [...], chance: 0.0}}
    ancient-city: {item: ancient_resonator, loot: {tables: ["minecraft:chests/ancient_city"], chance: 0.01}}
```

- **Welcher Schlüssel:** einer je Portaltyp. Schreine nehmen den Schlüssel ihrer Dimension, das Portal im
  Zentrum der Antiken Stadt den **Antiken Resonator**. Den gibt es nur als seltenen Fund in den Truhen der
  Antiken Stadt (ohne Rezept); er ersetzt die geheime Melodie und das Buch.
- **Verbrauch:** Der Schlüssel wird verbraucht, wenn er ein schlafendes Portal öffnet. Ein Portal, dessen
  Konsolen schon wach sind (vor alpha.37 geweckt, oder die Zündung wurde unterbrochen, z. B. weil ein
  Notenblock fehlte), öffnet derselbe Schlüssel ohne Verbrauch.
- **Falscher Schlüssel:** verzerrter Klang und Hinweis, nichts wird verbraucht.
- **Singer:** Das Ziegenhorn ruft ihn weiter zu einem schlafenden Portal, und er singt. Seine Schallwellen
  lassen die Konsolen nur noch aufleuchten; sie wecken sie nicht mehr. Ausschalten:
  `mobs.singer.summon.enabled: false`.
- **Nicht mehr benutzt:** `portals.melody.*`, `portals.ancient-city.melody`, `portals.ancient-city.book`. Stehen
  sie noch in der `config.yml`, meldet das Plugin beim Start eine Warnung; die Einträge können gelöscht werden.
  Alte Melodie-Bücher bleiben gewöhnliche Bücher. Die Melodien im Portal-Register bleiben gespeichert, werden
  aber nicht mehr verwendet.
- **Portale aus Spieltests:** `/sift portal lock <id>` legt ein Portal wieder schlafen (Konsolen im Horn-Modus,
  Fläche zu); danach braucht es wieder einen Schlüssel. Siehe GOLIVE.md.

**Rezepte (`portals.recipes`), gestaffelt Hauptwelt < Nether < End:**
- Zutaten: Vanilla-Items (`minecraft:x`), Tags (`#minecraft:x`) und Sift-Items (`the_sift:x`, z. B. spätere
  Boss-Items aus Phase 4). Ein Item je Feld des 3 × 3-Rasters.
- Ein zweites Rezept für denselben Schlüssel: neuer Name unter `portals.recipes`, gleiches `result`.
- **Ungültiges Rezept** (Tippfehler, Item gibt es nicht oder noch nicht): Das Plugin meldet es beim Start im Log
  und nimmt stattdessen das eingebaute Rezept gleichen Namens; ohne eingebautes Rezept wird es weggelassen.
- **Update der config.yml (seit alpha.37):** Neue Einstellungen kommen beim Start dazu, aber nie in ein Rezept,
  das schon in der Datei steht (bis alpha.36 wurden dabei Zutaten alter und neuer Rezepte vermischt). Rezepte
  und Beute-Chancen, die noch genau den Standardwerten von alpha.36 entsprechen, ersetzt das Plugin einmalig
  durch die neuen und schreibt das ins Log („old defaults replaced“). Selbst geänderte Werte bleiben.

### Portal-Schreine (Schritt 7b)

In Hauptwelt, Nether und End baut das Plugin beim Generieren neuer Chunks je einen Schrein pro Zelle
(`portals.structures.<overworld|nether|end>`, Neustart nötig).

**Aufbau (alle drei):**
- Boden 15 × 15 mit Eckpfeilern und niedrigen Mauern.
- Rahmen 7 × 8 aus Verstärktem Tiefenschiefer (Öffnung 5 × 6).
- Davor 8 schlafende Konsolen in zwei Reihen, auf jeder ein Notenblock mit zufälligem Ton.

| Dimension | Aussehen | Häufigkeit (Standard) | Gelände |
|---|---|---|---|
| Hauptwelt | eingesunkene, verfallene Tiefenschiefer-Ruine mit Sculk | 1 je 64 × 64 Chunks | über Wasser auf einem Felssockel |
| Nether | Schrein aus Schwarzstein und Basalt, Seelenerde, Seelenfeuer, Seelenlaternen | 1 je 16 × 16 Chunks | im niedrigsten Hohlraum zwischen y 32 und 100, sonst auf y 70 ausgehöhlt; Lava am Rand wird zu Schwarzstein, über Lava steht er auf Basalt |
| End | Schrein aus Purpur und Endsteinziegeln mit Endstäben | 1 je 32 × 32 Chunks, erst ab 1100 Blöcken von 0/0 | auf der Insel; über der Leere auf einer kleinen Endstein-Insel |

- Jede Zelle hat genau einen Schrein im mittleren Drittel der Zelle; gebaut wird immer.
- Der Ort hängt nur vom Seed ab. Deshalb kennen Kompass und `/sift portal locate` auch Schreine in noch
  nicht generierten Gebieten.
- Ein gebauter Schrein meldet sich als Portal `shrine-<chunkX>-<chunkZ>` an (Zustand LOCKED).

**Eigene Vorlage statt des eingebauten Schreins:** `plugins/TheSift/structures/<overworld|nether|end>.nbt`
(z. B. mit Axiom gebaut und per Strukturblock gespeichert, höchstens 16 × 16 Blöcke).
- Die unterste Schicht ist der Boden.
- Die Vorlage braucht einen Rahmen aus Verstärktem Tiefenschiefer (3–23 Blöcke, innen frei) und
  Klingenden Tiefenschiefer als Konsolen. Notenblöcke darin bekommen zufällige Töne.
- Beim Start prüft das Plugin die Vorlage. Eine fehlerhafte Vorlage meldet es im Log und baut dann den
  eingebauten Schrein.
- Gilt nur für neue Chunks.

### Schlüssel und Sift-Kompass

| Item | Öffnet | Rezept (Standard, `portals.recipes`) | Beute (`portals.keys.*.loot`) |
|---|---|---|---|
| Echo-Resonator | Schreine der Hauptwelt | Schwerer Kern, 4 Echo-Splitter, Sculk-Katalysator | Antike Stadt, 3 % |
| Seelenglut-Kern | Schreine im Nether | 3 Wither-Skelett-Schädel, Netherit-Barren, 2 Ghast-Tränen, Lohenrute | Bastionen, 3 % |
| Leere-Resonator | Schreine im End | Drachenkopf, Elytren, 2 Shulkerschalen, Geplatzte Chorusfrucht | – |
| Antiker Resonator | Portal der Antiken Stadt | – | Antike Stadt, 1 % |
| Sift-Kompass | – | Kompass, 4 Echo-Splitter, 4 Amethystsplitter | – |

- **Erreichbarkeit:** Den Schweren Kern gibt es nur aus Ominous-Tresoren der Prüfungskammern. Drachenkopf und
  Elytren liegen auf Endschiffen, also auch nach dem ersten Drachenkill. Drachenatem (nur bei lebendem Drachen)
  ist deshalb nicht mehr im Rezept.
- **Einstieg:** Offene Portale bleiben offen und gelten für alle; ein geöffneter Schrein reicht für den ganzen
  Server. Ein zweiter Weg hinein ist das Portal der Antiken Stadt mit dem gefundenen Antiken Resonator.
- **Sift-Kompass:** Solange man ihn hält, zeigt die Nadel auf den nächsten Schrein der Dimension, in
  der Sift auf das Haupt-Portal. Die Actionbar zeigt die Entfernung; aufgefrischt wird alle
  `portals.compass.update-ticks` (40).
- **Admins:** `/sift portal locate` nennt den nächsten Schrein mit Teleport-Klick.
- **Konsolen außerhalb der Sift:** Klingender Tiefenschiefer darf außerhalb der Sift stehen
  (`custom-blocks.placeable-outside-sift`). Alle anderen Sift-Blöcke bleiben dort gesperrt.

**Texturen ersetzen:** Die Texturen der Schlüssel und des Kompasses hat
`scripts/gen_portal_item_textures.py` gezeichnet. Eigene PNGs (16 × 16) unter denselben Namen ersetzen
sie, danach das Resource Pack neu bauen bzw. veröffentlichen:
- `pack-src/resourcepack-overlay/assets/the_sift/textures/item/echo_resonator.png`, `soul_ember_core.png`,
  `void_resonator.png`, `ancient_resonator.png` (seit alpha.37)
- `…/textures/item/sift_compass_00.png` … `sift_compass_31.png`: eine Nadelstellung je Bild, wie beim
  Vanilla-Kompass.
  - `16` zeigt geradeaus (nach oben), jedes weitere Bild 11,25° im Uhrzeigersinn weiter: `20` schräg
    rechts, `24` rechts, `00` unten, `08` links.
  - Das Skript schreibt auch die Modelle; die PNGs allein lassen sich jederzeit tauschen.

| Befehl | Wirkung |
|---|---|
| `/sift portal create <x y z>` | Rahmen aus Verstärktem Tiefenschiefer an dieser Stelle als Portal anlegen; Konsolen im Umkreis gehören dazu. Nicht in der Sift. |
| `/sift portal lock [id]` | Wieder schlafen legen (Konsolen im Horn-Modus, Fläche zu); danach braucht es wieder einen Schlüssel. Für Portale aus Spieltests (seit alpha.37). |
| `/sift portal activate [id]` | Öffnen wie mit dem Schlüssel (Konsolen erwachen, Zündung, etwa 15 s); bei offenem Portal: Fläche erneuern |
| `/sift portal deactivate [id]` | Schließen; Konsolen bleiben wach. Das Haupt-Portal der Sift bleibt immer offen. |
| `/sift portal remove <id>` | Abmelden (Blöcke bleiben stehen) |
| `/sift portal info [id]`, `list` | Zustand, Rahmen, Konsolen mit ihrer Klangnummer 1–8, passender Schlüssel; Liste aller Portale mit Teleport |
| `/sift portal locate` | Nächster Portal-Schrein der Welt (berechnet, auch ungeneriert) mit Teleport-Klick |

Ohne `id` wirkt der Befehl auf das nächste Portal im Umkreis von 64 Blöcken. Von der Konsole aus:
`/execute in minecraft:overworld run sift portal …`.

### Feuer und Brennstoff

Weidenstamm, -holz, -bretter, -laub und -setzling brennen wie im Original und taugen als Ofen-Brennstoff
(`blocks.<id>.fire`, `blocks.<id>.fuel`; Einzelheiten in `docs/ABWEICHUNGEN.md`). Die Gamerule
`fire_spread_radius_around_player` gilt auch dafür.

## Abbauzeit der Sift-Blöcke

Die Sift-Blöcke stehen auf Vanilla-Ersatzblöcken (Pilzblöcke, Stolperdraht, Chorus). Ein vorübergehender Modifikator auf
`player.block_break_speed` gleicht die Abbauzeit an den Sift-Block an. Seit alpha.35 wird er schon für den Block im
Fadenkreuz gesetzt (`custom-blocks.break-speed-target-ticks`, Standard 2) und beim Linksklick, weil Paper einen
Sofortabbau vor dem Schlag-Event entscheidet. `/sift debug break <x> <y> <z> <werkzeug>` zeigt die erwarteten Ticks.

## Mobs (Phase 2)

### Allays (ab alpha.31)

`mobs.allay.{enabled, interval-ticks, attempts, min-distance, max-distance, local-radius, local-height}` und
`profiles.<biom>.{chance, min-group, max-group, local-cap}` (Werte des Originals: Überwucherter Wald 0,72/2–4/16,
Sift-Ödland 0,5/2–3/12, Lichtung 0,32/1–3/8). Vanilla-Allays, unabhängig von `mobs.enabled`.
`/sift debug allays` zeigt Zähler und die nächsten Allays mit Teleport.

Jeder Sift-Mob ist ein unsichtbarer, stummer Vanilla-Mob (Hitbox, Wegfindung, Kampf) mit dem Eintrag
`the_sift:mob` in seinen Entity-Daten. Sein Modell besteht aus Item-Displays (eines je Würfel des
Original-Modells), die auf ihm reiten. Die Displays werden nicht gespeichert: Beim Laden des Chunks, nach
einem Neustart oder einer Portalreise baut das Plugin sie neu. Bedrock-Spieler sehen die Mobs nicht.

- **Einstellungen:** `config.yml` → `mobs` (`[Start]`, Neustart nötig):
  - `render`: Posen alle `update-ticks` (2 = 10/s), Interpolation, Sichtweite (48 Blöcke), Treffer-Farbe.
  - `limits`: höchstens `per-world` (150) Sift-Mobs je Welt; natürliches Spawnen nur, solange um den
    Spieler (`player-radius` 64) weniger als `per-player` (8) sind.
  - `mobs.<id>`: `enabled`, Größe, Attribute, Verhalten, Erfahrung und `spawn` (Intervall, Abstand,
    Gruppengröße, Profile je Biom mit Chance und Grenze).
- **Texte:** `plugins/TheSift/messages.yml` (MiniMessage, deutsch). Neue Texte eines Updates werden beim
  Start ergänzt, eigene Änderungen bleiben.
- **Abschalten:** `mobs.<id>.enabled: false` stoppt Spawnen, Spawn-Eier und `/sift spawn`. Vorhandene
  Mobs dieser Art bleiben als unsichtbare Basis-Mobs in der Welt und sind nach dem Wiedereinschalten
  wieder da. Zum Entfernen: `/kill @e[type=wolf,nbt={BukkitValues:{"the_sift:mob":"blub"}}]`.
- **Modelle ersetzen:** Würfel-Modelle unter `assets/the_sift/models/item/mob/<variante>/<n>.json`,
  Texturen unter `assets/the_sift/textures/item/mob/<variante>.png` (packgen erzeugt sie aus dem
  Original-Jar). Die Rig-Daten liegen im Plugin unter `mobs/`.
- **Leistung:** Posen gehen nur an Spieler, die das Modell sehen, und nur für Teile, die sich bewegt
  haben. `/sift debug mobs` zeigt die Transformationen pro Sekunde.

### Blub

Basis Wolf (Größe 1,1). Zähmen mit einem Seelenblock (1 zu 3), Sitzen per Rechtsklick des Besitzers,
Heilen mit Seelenblock oder Essen, Rudelzorn (16 Blöcke, 30 s). Spawnt selten in den Überwucherten Biomen
(im Original nur per Spawn-Ei); Spawn-Ei `blub_spawn_egg` über `/sift items` bzw. `/sift give`.

### Sifter

Basis Schwein (Hitbox 0,9 × 0,9). Feindlich: jagt Spieler in 28 Blöcken, beißt mit 10 Schaden, 40 HP.
Spawnt nach den Profilen des Originals in der ganzen Sift (`mobs.sifter.spawn`), nur auf den Blöcken unter
`floor-blocks`; auf Friedlich keine Sifter. Beute rohes Sifterfleisch, 7 Erfahrung. Spawn-Ei
`sifter_spawn_egg`.

### Singer

Basis Fahrender Händler (Größe 2,05). **Gerufen:** Ein Ziegenhorn in 40 Blöcken um ein schlafendes Portal
ruft ihn nach 3 s; er singt, seine Schallwellen lassen die Konsolen nacheinander aufleuchten, dann taucht er
ab. Seit alpha.37 weckt er die Konsolen nicht mehr (nur der Schlüssel öffnet ein Portal);
`mobs.singer.summon.enabled: false` schaltet den Ruf ab. `/sift debug singer` ruft ihn ohne Horn. **Bleibend:** `/sift spawn singer` bzw.
`singer_spawn_egg`: läuft im Umkreis `mobs.singer.leash-radius` umher und tauscht Seelenblöcke gegen
Belohnungen aus `mobs.singer.barter.table` (Gewicht von 200, Anzahl).

Schlüssel abschalten: `portals.keys.enabled: false` (Neustart). Dann öffnen nur noch Admins Portale
(`/sift portal activate`); offene Portale bleiben offen, und die Schlüssel liegen nicht mehr in Truhen.

### Dunkler Schnüffler

Basis Eisbär (Größe 1,36), eigenes vereinfachtes Modell auf der Original-Textur. Feindlich gegen Spieler und
normale Schnüffler in 32 Blöcken (Biss 7, Schallwelle zieht heran, ohne Schaden), gräbt Sculk nach
Sculkblumensamen, infiziert normale Schnüffler, die auf Sculk graben (nach 30 s werden sie zu Dunklen
Schnüfflern; Log-Zeile). Spawnt beim Generieren: 1–2 je neuer Sculk-Region an der Oberfläche der Sift.
Einzeln schaltbar unter `mobs.dark_sniffer`: `sonic.enabled`, `dig.enabled`, `infection.enabled`,
`attack-sniffers`, `spawn.enabled`, `wardens-friendly` (Wächter greifen ihn nicht an), `wardens-pet`
(ruhige Wächter streicheln ihn ab und zu). Spawn-Ei `dark_sniffer_spawn_egg`. `/sift debug infect` infiziert
den nächsten normalen Schnüffler sofort (Test der Infektion).

### Namen der Sift-Mobs (ab alpha.19.2)

Jeder unsichtbare Basis-Mob trägt den Namen seines Sift-Mobs (`messages.yml` → `mobs.names`) als unsichtbaren
Namen, damit Todesnachrichten und `/kill` „Sifter“ statt „Schwein“ sagen. Ein Namensschild erscheint nicht
(unsichtbare Mobs zeigen keinen Namen). Nebenwirkung: Spigot schreibt für benannte Mobs beim Tod eine Konsolenzeile
„Named entity … died: …“; abschalten in `spigot.yml` mit `settings.log-named-deaths: false`. Ein Name macht Mobs nicht
dauerhaft, natürliche Sifter verschwinden weiter.

### Echo Golem (ab alpha.19)

Basis Schaf (Größe 1,28, Hitbox 1,15 × 1,66, Stufenhöhe 1,5), friedlich, verschwindet nie. Er sucht die nächste
Seelenschlucht mit geladenen Seelenblöcken (bis 1024 Blöcke), steigt über Eingang und Mitte hinab, hebt einen
Seelenblock auf (der Block wird zu Luft; nur in der Sift, `pickup.enabled`, Gamerule `mob_griefing`) und klettert
wieder hinaus. Ungebunden ruft er einen **Seelen-Singer** (erscheint 7–12 Blöcke entfernt, nimmt den Block unter
einer Verbeugung an, Advancement „Like father and son“ für Spieler in 16 Blöcken, taucht nach 30 s ohne Golem
wieder ab). Mit einem **Echo-Splitter** (Rechtsklick) wird er an den Spieler gebunden und bringt ihm den Block ins
Inventar (Advancement „Friend of the Sift“; volles Inventar: vor die Füße; nur im Überlebens- oder Abenteuermodus).
Rechtsklick seines Spielers übergibt sofort (`bonded.right-click-gives`). Jeder Treffer und jeder Rechtsklick ohne
Splitter auf einen ungebundenen Golem lässt den Block
fallen; danach sitzt er 30 s. Keine Beute, Erfahrung 4–7. Spawn-Ei `echo_golem_spawn_egg`.

Erscheinen (`mobs.echo_golem.spawn`): alle 80 Ticks zuerst an einem Schlucht-Eingang, der noch „Golem ausstehend“
ist (jede neue oder per Befehl gebaute Schlucht bekommt so einen Golem), sonst um Spieler ohne Golem in
165 × 96 × 165, 82–160 Blöcke entfernt. Golems zählen zu `mobs.limits.per-world`.

Leuchten beim Tragen (`mobs.echo_golem.glow`):
- `model` (Standard an): leuchtender Seelenblock im Modell.
- `light-block` (Standard an): unsichtbarer Lichtblock der Stufe `level` (14) im Kopfblock des Golems.
  - Gesetzt wird er nur in Luft und nur, wenn der Kopf den Block wechselt.
  - Entfernt wird er nur, wenn dort noch unser Lichtblock steht; jede Position steht vorher in den Daten ihres Chunks.
  - Er verschwindet beim Abgeben und Fallenlassen, bei Tod und Entfernen (einen Tick später) und beim
    Plugin-Stopp oder Serverstopp.
  - Übrig gebliebene Lichtblöcke (Absturz, Chunk entladen) räumt das nächste Laden des Chunks auf.
  - Abschalten mit `light-block: false`; das Aufräumen läuft trotzdem weiter.

Weitere Werte: `attributes`, `timings`, `search`, `delivery`, `speeds`, `paths-per-tick` (Wege je Tick für alle
Golems), alle wie im Original. Der Seelen-Singer steht unter `mobs.singer.soul-event` (`enabled`, `search`,
`roam-ticks`).

`/sift debug golems` zeigt:
- erschienene Golems, ausstehende Eingänge und die Zähler (aufgehoben, an Singer, an Spieler, fallen gelassen);
- die Lichtblöcke in 48 Blöcken um dich (davon von tragenden Golems, übrig);
- je Golem eine Zeile mit Position, Zustand, Ziel, Schlucht und Bindung.

`/sift debug mobs` zeigt den Zustand des nächsten Golems bzw. Singers.

### Portal der Antiken Stadt

`portals.ancient-city` (Standard an, `[Start]`, nur neue Chunks): Das Zentrum jeder neuen Antiken Stadt wird
das Portal des Originals. Es wird beim Generieren registriert (`city-…` in `/sift portal list`, Log-Zeile
„Ancient City portal … registered“). Seit alpha.37 öffnet es nur der **Antike Resonator**
(`portals.keys.ancient-city`), der mit 1 % in den Truhen der Antiken Stadt liegt und kein Rezept hat. Die
frühere Melodie und das Buch „Lied der Tiefe“ gibt es nicht mehr.
Mit `enabled: false` bleiben neue Städte Vanilla; vorhandene Portale bleiben registriert.

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

## Ichor (ab alpha.21)

In der Sift ist alles Wasser Ichor; es ist Vanilla-Wasser mit der Biomfarbe `#9dc5ff`. Einstellungen unter
`ichor.*` (alles einzeln schaltbar, `ichor.enabled: false` schaltet alles ab):

| Option | Wirkung (Werte des Originals) |
|---|---|
| `regeneration` | Lebewesen im Ichor bekommen Regeneration I (101 Ticks, ohne Partikel, mit Symbol), alle 10 Ticks erneuert |
| `drag-mobs` | Mobs und Items werden im Ichor gebremst (×0,92 waagrecht, ×0,94 senkrecht je Tick); Spieler nicht |
| `source-conversion` | `false` (Standard): Gamerule `water_source_conversion` der Sift aus, keine unendlichen Quellen |
| `lava-results` | Fließende Lava an Ichor → Siftschiefer-Bruchstein, Lava auf Ichor → Siftschiefer, Lavaquelle → Obsidian |
| `bucket` | Eimer an Ichor oder vollem Kessel in der Sift → Ichoreimer (Advancement „Blut der Götter“) |
| `bottles` | Glasflasche an Ichor-Quelle oder Kessel in der Sift → Ichorflasche (Kessel −1); Ichorflasche auf Kessel → +1 Stufe |
| `mist` | Staubpartikel in Ichorfarben über Quellblöcken nahe Spielern (Takt, Radius, Anzahl) |

Junge Sniffer, die in der Sift in oder direkt über Ichor erscheinen, werden verhindert (wie im Original).

### Ichor bleibt Ichor (ab alpha.38)

```yaml
ichor:
  identity:
    enabled: true       # false = wie bis alpha.37: in der Sift ist alles Wasser Ichor, sonst alles Wasser
    outside-sift: true  # Regeneration und Bremsung auch in ausgegossenem Ichor außerhalb der Sift
```

- **Standard:** In der Sift ist Wasser Ichor, sonst Wasser. Gespeichert werden nur **Ausnahmen**, je Block in den
  Chunk-Daten (`the_sift:fluid_marks`): Wasser aus einem Wassereimer in der Sift, Ichor aus einem Ichoreimer
  außerhalb. Alte Chunks brauchen nichts.
- **Woher Markierungen kommen:** Eimer und Werfer beim Ausgießen (nicht in schon vorhandenes Wasser), Ichorflasche
  bzw. Eimer in einen Kessel; fließendes Wasser erbt die Markierung seines Ursprungs.
- **Wann sie gehen:** Eimer oder Werfer nimmt das Wasser auf, Schwamm, Kolben (der geschobene Block verliert sein
  Wasser), Kessel geleert. Markierungen ohne Wasser werden beim Entladen des Chunks entfernt; Eis behält seine
  (geschmolzen ist es wieder dieselbe Art).
- **Wirkung:** Eimer, Werfer (leerer Eimer an Ichor ergibt einen Ichoreimer), Glasflasche, Kessel, Regeneration und
  Bremsung richten sich nach der Art. Außerhalb der Sift laufen Regeneration und Bremsung nur in Welten, die
  Markierungen haben.
- **Nicht erfasst:** `/fill`, `/setblock`, WorldEdit und andere Plugins setzen Wasser ohne Markierung.
- **Last:** Jedes Fließen ruft das Plugin auf; in einer Welt ohne Markierungen kehrt es sofort zurück. Gemessen im
  Smoke-Test: etwa 8 µs je Fließen im Mittel.
- `/sift debug ichor marks`: Markierungen je Welt (geladene Chunks), Zahlen und Zeit des Fließ-Handlers (Mittel,
  höchstens), und welche Art das Wasser an der eigenen Position hat. Von der Konsole:
  `/execute in minecraft:overworld positioned <x y z> run sift debug ichor marks`.

## Siftit-Erhalt beim Tod (ab alpha.23)

Wie im Original 1.1.1:
- **Beim Tod** (nicht bei `keepInventory`) werden alle Siftit-Gegenstände (Tag `the_sift:siftite_items`:
  Erz, Klumpen, Barren, Vorlage, Werkzeuge, Speer, Rüstung) gesichert.
  - Ort: in den Spielerdaten (`the_sift:siftite_pending`).
  - Erst wenn die Sicherung zurückgelesen vollständig ist, werden sie aus den Drops genommen.
  - Das Plugin greift früh ein (Priorität LOW), damit Grab- oder Rucksack-Plugins sie nicht vorher nehmen.
- **Rückgabe:** 2 s nach Respawn oder Login öffnet sich neben dem Spieler ein Mini-Riss. Alle 5 Ticks fliegt
  ein Gegenstand heraus, der Reihe nach zum Spieler, mit `END_ROD`-Partikeln.
  - Ein Gegenstand verlässt die Sicherung erst im selben Moment, in dem er im Inventar landet. Bei Logout,
    Neustart oder Absturz während der Lieferung geht nichts verloren und nichts wird doppelt: Nach dem
    nächsten Login geht es weiter.
  - Bei vollem Inventar kreisen die Gegenstände, bis Platz ist (Hinweis in der Actionbar).
  - Rüstung wird nicht wieder angelegt.
- **Advancement** „Keep Inventory“ bei der ersten Rückgabe.
- **`keep_inventory` gilt in 26.3 je Welt:** `/gamerule keep_inventory …` ändert nur die Welt, in der der Befehl
  läuft (von der Konsole: die Hauptwelt; für die Sift `/execute in the_sift:the_sift run gamerule …`).
  Maßgeblich ist die Welt, in der der Spieler stirbt. Ist es dort `true`, behält Vanilla das ganze Inventar
  und es wird nichts gesichert; es geht aber auch nichts verloren. `/sift siftite list` zeigt den Wert je Welt.
- Bricht ein anderes Plugin den Tod ab oder schaltet `keepInventory` nachträglich ein, wird die Sicherung
  wieder entfernt, damit nichts doppelt entsteht.

Einstellungen: `siftite.keep-on-death.*` (`enabled`, `delay-ticks`, `emit-interval-ticks`, `open-ticks`,
`particles`).

| Befehl | Wirkung |
|---|---|
| `/sift siftite list [Spieler]` | Gesicherte Siftit-Stapel, die noch nicht zurück sind (auch für Offline-Spieler; ohne Namen: man selbst), dazu `keep_inventory` je Welt |
| `/sift siftite restore <Spieler>` | Notausgang: alles Gesicherte sofort ins Inventar (Rest vor die Füße, nur für diesen Spieler aufhebbar), Sicherung geleert, laufende Lieferung beendet |

## Risse (ab alpha.22)

Wie im Original 1.1.1:
- **Natürliche Risse** öffnen sich nur in der Sift, je Spieler alle 4,5–5,5 Minuten, 20–64 Blöcke entfernt.
  - Platzbedarf: 9 × 5 Blöcke Luft oder Pflanzen, fester Boden 1–3 darunter.
  - Sie bleiben 5 Minuten offen und führen **hinaus** zum Respawnpunkt (Bett oder Seelenanker in der
    Hauptwelt), sonst zum Welt-Spawn.
- **Gegen-Riss:** Dort öffnet sich ein Riss, der bis zum Schließen des Ursprungs zurückführt. Ein offener
  Gegen-Riss in `counter.reuse-radius` (64) wird weiterverwendet.
  - Sonst wird in Ringen von `counter.min-radius` bis `counter.max-radius` (16–256, Schritt 16, 16 Punkte)
    gesucht.
  - Nicht im Spawn-Schutz (`spawn-protection` in `server.properties`) und nur, wo der Spieler bauen darf.
    Gefragt wird über ein Test-Ereignis, das Claim-Plugins wie GriefPrevention, WorldGuard oder Lands
    abbrechen.
  - Findet sich kein Platz, landet der Spieler am Respawnpunkt und bekommt einen Hinweis.
  - Blätter, Weidenlaub und Pflanzen zählen nicht als Boden; Baumkronen werden übergangen (ab alpha.23.1).
- **Alle Wesen reisen mit** (Spieler, Mobs, Items, nicht Zuschauer).
  - Mobs und Items, die hinausgehen, landen am Welt-Spawn ohne Gegen-Riss.
  - Nach einer Reise wirkt kein Riss, bis man ihn verlassen hat und `reentry-cooldown-ticks` (100) vorbei
    sind.
- **Hinein** (Gegen-Riss oder gesetzter Riss außerhalb der Sift): zurück zum Ursprungs-Riss, sonst zum
  Haupt-Portal der Sift. Die Zeitsperre (`access.*`) gilt; der Rückkehrpunkt wird am Riss gesetzt.
- **Mobs meiden Risse:** Pfade, die in die Nähe führen, werden abgebrochen (`mob-avoidance`).
- **Licht:** 3 Lichtblöcke der Stufe 15 (`light`), nur in Luft.
  - Sie stehen auch im Chunk-Speicher und werden beim Schließen, beim Serverstopp und nach einem Absturz
    (beim Laden des Chunks) entfernt.
  - Offene Risse überstehen keinen Neustart.
- **Klänge:** `entity.rift.appear` beim Öffnen; Leuchtfeuer-Summen in 32 Blöcken (`ambient-sound`).
- **Advancements:** „Rifter“ bei jeder Reise. „Rift Resonance“: die Schallplatte „Rift“ in eine leere
  Jukebox in 16 Blöcken eines offenen Risses legen.
- **Item `sift_rift`** (`/sift give <Spieler> sift_rift`): nur im Kreativmodus oder mit `thesift.admin`.
  Rechtsklick auf einen Block öffnet dort einen Riss (in der Sift: hinaus, sonst in die Sift). Es wird
  nicht verbraucht.

Alles steht unter `rifts.*`; `rifts.enabled: false` schaltet Risse ganz ab.

| Befehl | Wirkung |
|---|---|
| `/sift rift open [x y z]` | Riss 5 Blöcke vor dir öffnen (mit Position: dort; auch von der Konsole mit `/execute in <welt> run …`). Ab alpha.23.1 wird im Umkreis `rifts.admin.search-radius` (12, Höhe ±6) der nächste Platz mit 9 × 5 Blöcken Luft gesucht; Boden ist nicht nötig. Risse liegen nie in, auf oder unter einem anderen Riss |
| `/sift rift close` | Nächsten offenen Riss in 32 Blöcken schließen |
| `/sift rift list` | Alle offenen Risse mit Ziel und Teleport-Klick; dazu Zähler der natürlichen Platzsuche (Suchen, geöffnet, aufgegeben, ungeladene Chunks) und Riss-Displays ohne Riss (werden alle 10 s entfernt) |

## Zugang zur Sift (ab alpha.20)

Die Sift lässt sich zeitlich sperren, z. B. bis eine Woche nach dem ersten Drachenkill. Gesperrt ist nur der
Weg **hinein**: Öffnen der Portale mit dem Schlüssel geht weiter, und hinaus (durch das
Haupt-Portal) geht es immer.

```yaml
access:
  mode: always              # always | after-dragon | fixed-date | manual
  delay-days: 7             # after-dragon: Tage nach dem ersten Drachenkill (Kommazahlen erlaubt)
  date: ""                  # fixed-date: ISO, z. B. "2026-12-01T18:00" (ohne Zone = Server-Zeitzone) oder "2026-12-01T18:00+01:00"
  close-warning-seconds: 30 # Vorwarnung beim Schließen
  dragon-worlds: []         # Welten, deren Drachenkill zählt; leer = jede End-Welt
```

- **always:** immer offen (Standard, Verhalten wie vor alpha.20).
- **after-dragon:** offen ab erstem Drachenkill + `delay-days`. Der erste Kill wird **einmal** mit Zeit und
  Welt im Weltspeicher der Hauptwelt gespeichert (`the_sift:dragon_first_kill`, `…_world`); spätere Kills
  ändern nichts. Wurde der Drache schon besiegt, bevor das Plugin lief, ist die Zeit unbekannt: Die Sift
  bleibt zu, bis ein Admin `/sift access set dragon-kill <Datum>` setzt (Warnung in Konsole und `status`).
- **fixed-date:** offen ab `date`. Ohne gültiges Datum bleibt sie zu (Warnung beim Laden).
- **manual:** zu, bis `/sift open`.
- `/sift open` und `/sift close` setzen einen **Befehlszustand**, der den Modus überstimmt und Neustarts
  übersteht (`the_sift:access_override`). `/sift access reset` kehrt zum Modus zurück.

**Solange die Sift zu ist:**
- Portale führen niemanden hinein: Spieler ohne Bypass bekommen eine Meldung in der Actionbar (mit
  Restzeit, wenn bekannt), Mobs und Items bleiben still draußen.
- Teleports in die Sift (`/tp`, andere Plugins, `/sift tp`) werden abgebrochen, Respawn in der Sift geht an
  den Spawn der Hauptwelt, und wer sich in der Sift einloggt, wird zurückgeschickt.

**Beim Schließen** (Befehl, Modus oder Reload) läuft ein Countdown von `close-warning-seconds` (Chat beim
Start, Actionbar jede Sekunde). Danach geht jeder ohne Bypass zu seinem Rückkehrpunkt (wo er durch ein Portal
hinein ist), sonst zum Spawn der Hauptwelt. Wird vorher wieder geöffnet, bricht der Countdown ab.
**Beim Öffnen** bekommt der ganze Server eine Ankündigung (`messages.yml access.opened`).

**Bypass:** Recht `thesift.access.bypass` (Standard: OP). Es ist **nicht** in `thesift.admin` enthalten,
damit Admins die Sperre auch selbst testen können. Mit LuckPerms:

```
lp user <Spieler> permission set thesift.access.bypass true
lp group admin permission set thesift.access.bypass true
```

Wer OP ist, hat es automatisch; zum Testen ohne Bypass `lp user <Spieler> permission set thesift.access.bypass false`.

| Befehl | Wirkung |
|---|---|
| `/sift access [status]` | Offen/zu, Grund, Modus, Befehlszustand, Öffnungszeit mit Restzeit, erster Drachenkill mit Welt, laufender Countdown, Zeitzone |
| `/sift access open` / `/sift open` | Öffnen (Befehlszustand, bleibt nach Neustart) |
| `/sift access close` / `/sift close` | Schließen mit Countdown (Befehlszustand, bleibt nach Neustart) |
| `/sift access reset` | Befehlszustand löschen, es gilt wieder `access.mode` |
| `/sift access set dragon-kill <ISO-Datum\|now\|clear>` | Zeitpunkt des ersten Drachenkills setzen oder löschen |
| `/sift access reload` | Nur den Abschnitt `access` aus `config.yml` neu lesen (Datei asynchron gelesen) |

Texte stehen in `messages.yml` unter `access.*`.

## Aufbau der config.yml (ab alpha.39)

Die Abschnitte der obersten Ebene, in der Reihenfolge der Datei (dieselbe Übersicht steht als Kommentar am Anfang der
`config.yml`). Umbenannt oder verschoben wird nichts; neue Einstellungen fügt das Plugin beim Start selbst hinzu.

| Abschnitt | Inhalt | Hier beschrieben |
|---|---|---|
| `custom-blocks` | Platzieren, Abbau, Update-Schutz der Sift-Blöcke | Wie eigene Blöcke funktionieren |
| `blocks` | Härte, Werkzeug, Drops, Feuer je Sift-Block | Drops und Erze |
| `plant-support` | Untergrund für Sift-Pflanzen | Wie eigene Blöcke funktionieren |
| `worldgen` | Weltgenerierung der Sift `[Start]` | Weltgenerierung |
| `admin` | Admin-Befehle mit Schutzschalter (Spieler-Reset) | GOLIVE.md |
| `weather` | Wetter und Ichor-Schneefall | Weltgenerierung → Wetter |
| `atmosphere` | Intro-Klang | Weltgenerierung → Atmosphäre |
| `ichor` | Ichor-Verhalten, Identität (ab alpha.38) | Ichor |
| `rifts` | Risse | Risse |
| `siftite` | Siftit-Erhalt beim Tod | Siftit-Erhalt beim Tod |
| `access` | Zeitsperre der Sift | Zugang zur Sift |
| `portals` | Portale, Schreine, Schlüssel und ihre Rezepte, Kompass, Antike Stadt | Portale |
| `items` | Werte der Sift-Items | Items, Rezepte, Siftit |
| `mobs` | Sift-Mobs | Mobs |
| `recipes` | Rezeptbuch beim Join | Items, Rezepte, Siftit |
| `resource-pack` | Resource Pack | Resource Pack |
| `phase4` | Eigene Inhalte nach dem Original-Port: `boss-items` (ab alpha.39); später `enchanter`, `temples`, `bosses` | Boss-Items |

## Boss-Items (Phase 4, ab alpha.39)

Vier neue Items (episch, mit Glanz), Texturen aus `scripts/gen_boss_item_textures.py` (eigene PNGs gleichen Namens
ersetzen sie):

| Item | id | Woher (`phase4.boss-items`) |
|---|---|---|
| Herz des Wächters | `warden_heart` | jeder Wächter, den ein Spieler tötet (auch mit Pfeil) |
| Kern des Withers | `wither_core` | jeder Wither, den ein Spieler tötet |
| Drachenschuppe | `dragon_scale` | jeder Spieler, der dem Enderdrachen Schaden gemacht hat, bekommt eine ins Inventar (voll: vor die Füße) |
| Golem-Trophäe (Arbeitstitel) | `golem_trophy` | später vom Boss „Großer Echo-Golem“ (alpha.42); bis dahin nur `/sift give` |

- Je Item: `enabled`, `item`, `chance`, `amount` bzw. `amount-per-player`, `cooldown-hours` (Abklingzeit je Spieler,
  0 = keine; gemerkt im Spieler unter `the_sift:boss_drop_<id>`), beim Drachen `min-damage-share`.
- Gegen Farmen: kein Drop bei Tod durch Mobs, Lava, Befehle. Bei Wither-Farmen bei Bedarf `chance` senken oder
  `cooldown-hours` setzen.
- Der Drache zählt auch nach jeder Wiederbelebung mit Endkristallen.
- Als Zutat in den Schlüssel-Rezepten: `the_sift:warden_heart` usw. in `portals.recipes` eintragen (Neustart).
- Später Zutaten für den Sculk Enchanter (alpha.40).

## Befehle (`thesift.admin`)

| Befehl | Wirkung |
|---|---|
| `/sift tp` | In die Sift (zum Haupt-Portal) bzw. zurück in die Hauptwelt |
| `/sift items` | Item-Menü (auch mit `thesift.admin.items`) |
| `/sift give <spieler> <id> [anzahl]` | Sift-Block oder -Item geben (auch mit `thesift.admin.items`) |
| `/sift pack info`, `reload`, `send <spieler>` | Resource Pack anzeigen, neu laden, erneut senden |
| `/sift rift open\|close\|list` | Risse (siehe „Risse“) |
| `/sift siftite list\|restore <Spieler>` | Siftit-Erhalt (siehe „Siftit-Erhalt beim Tod“) |
| `/sift access …`, `/sift open`, `/sift close` | Zugang zur Sift (siehe „Zugang zur Sift“) |
| `/sift spawn <mob> [anzahl]` | Sift-Mob an der eigenen Position erscheinen lassen (z. B. `blub`) |
| `/sift debug mobs` | Sift-Mobs je Welt und Art, Displays, Transformationen pro Sekunde, beim Laden entfernte verwaiste Modellteile, nächster Mob mit Hitbox |
| `/sift debug singer` | Singer zum nächsten schlafenden Portal in 40 Blöcken rufen (wie ein Ziegenhorn, ohne Verzögerung); er singt, weckt aber nicht (seit alpha.37) |
| `/sift debug golems` | Echo Golems: Erscheinen, Zähler der Seelenblöcke, Lichtblöcke in 48 Blöcken, Zustand jedes Golems |
| `/sift debug infect` | Nächsten normalen Schnüffler in 16 Blöcken sofort infizieren (verwandelt sich nach `infection.conversion-ticks`, nur in der Sift) |
| `/sift debug mobs walk <x> <y> <z>` | Nächsten Sift-Mob zu einem Block laufen lassen (Test der Wegfindung) |
| `/sift debug item <id>` | Komponenten eines Sift-Items anzeigen |
| `/sift debug break <x> <y> <z> <werkzeug> [none\|silk\|fortune1-3] [anzahl]` | Abbauzeit, Drops und XP eines Sift-Blocks berechnen, mit Statistik über viele Abbauvorgänge |
| `/sift debug find <id> [radius]` | Nächsten Sift-Block dieser Id suchen |
| `/sift debug ichor` | Nächste Ichor-Seen und -Quellen mit Koordinaten und Teleport (ab alpha.25) |
| `/sift debug worldgen` | Was die Weltgenerierung seit dem Start gesetzt hat (Weiden, Flecken, Sculk, verlassene Portale, Seelenschluchten mit Zeit je Kandidat) und warum Portale oder Schluchten ausfielen |
| `/sift debug canyon build` | Seelenschlucht an der eigenen Position (oder bis 32 Blöcke daneben) in geladenem Gelände bauen und eintragen (nur Testserver) |
| `/sift debug portals` | Alle protokollierten verlassenen Portale: gebaute mit Teleport, abgelehnte mit Grund |
| `/sift debug landmarks [x z [zellen]]` | Kandidaten-Chunks der verlassenen Portale um eine Position (Standard 0/0, ±1 Zelle) mit Status, die nächste Seelenschlucht (ohne x z: von der eigenen Position aus) mit Teleport, dazu die Mitten der Sculk-Regionen |
| `/sift debug heights <x> <z> <radius> <schritt>` | Höhenkarte der Sift als Text |
| `/sift debug scan [radius] [chunkX chunkZ]` | Chunks der Sift laden/generieren und Sift-Blöcke zählen |
| `/sift debug selftest` | Items, Rezepte, Siftit-Schutz, Loot und Advancements ohne Spieler prüfen |
| `/sift debug shape <x> <y> <z>` | Kollisionsboxen eines Blocks in der Sift (Pixel, Anteil am Würfel) |
| `/sift debug chorus <radius> <chunkX> <chunkZ>` | End-Chunks laden und zählen, ob Chorus dort Zustände der Sift hat (sollte 0 sein) |
