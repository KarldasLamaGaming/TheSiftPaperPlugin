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
Ichorglasscheibe, Siftschiefer an der Steinsäge) nehmen sie weiter an:

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
| Ichorflasche | `drinks`: Farbe, 3 s Regeneration; Füllen am Ichor kommt mit Phase 3 |
| Schallplatten Rift, Wellspring | `music-discs`: Lied aus dem Datapack; Rift liegt in Truhen verlassener Portale |

### Loot und Advancements

- Truhen-Loot verlassener Portale (`the_sift:chests/abandoned_portal`) kommt mit dem Datapack; das Plugin
  ersetzt die Sift-Items beim Erzeugen der Beute durch die echten Items.
- Die 22 Advancements des Originals sind im Datapack. Automatisch: Sift betreten, Biome erkunden, Siftit-
  Rüstung, Sculkblumensamen, antike Stätte. Vom Plugin vergeben: Charoiterz und Siftiterz abbauen, Holz in
  der Sift anzünden. Die übrigen (Mobs, Riss, Ichor, Charoit-Verzaubern, Siftit-Erhalt) kommen mit ihren
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
| Verlassene Portale mit Truhe (`the_sift:chests/abandoned_portal`) | alle außer Tiefes Dunkel | ein Kandidat je 20 × 20 Chunks (im mittleren Drittel der Zelle), davon baut nur der Anteil `chance` (Standard 0.25, siehe unten), nur auf passendem Gelände |

Weiden, Lotus, Sonnenstrahlpflanze usw. entstehen aus den Vorlagen und Werten des Originals; Abweichungen
in `docs/ABWEICHUNGEN.md`.

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

`worldgen.abandoned-portals.chance` (0.0–1.0, Standard 0.25) legt fest, welcher Anteil der Kandidaten
überhaupt bauen darf; die Auswahl hängt nur von Seed und Chunk ab. Im Original stehen auf vielen Plätzen
schon Seelenschluchten, Ichor-Seen, Lavaböden und Monolithen, die erst mit Phase 3 kommen. Mit 0.25 entsteht
bis dahin etwa die Menge des Originals (Seed `lamakingdom`, 49 Kandidaten: 7 gebaut, Original 6). Mit
Phase 3 wird der Wert 1.0. Nicht ausgewählte Kandidaten stehen in `/sift debug portals` als „nicht
ausgewählt (chance 0.25)“.

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
    Rechtsklick wählt den nächsten Klang, Schlagen spielt ihn.
- **Öffnen:** Die Melodie `portals.melody` (Original: 1-3-7-6-5-2-4-8) auf den Konsolen eines geweckten
  Portals spielen. Danach folgen ein Farbstoß, die Konsolen spielen die Melodie mit Lichtstrahlen nach,
  und die Fläche füllt sich vom Rand zur Mitte.
- **Offen** bleibt das Portal dauerhaft. Die rückwärts gespielte Melodie schließt es hier nicht, nur
  `/sift portal deactivate`.
- **Wecken:** Rechtsklick mit dem Schlüssel der Dimension auf eine schlafende Konsole (oder ihren
  Notenblock) weckt alle Konsolen des Portals; der Schlüssel wird verbraucht. Admins: `/sift portal unlock`.
- Die Fläche besteht aus unsichtbaren Lichtblöcken (Stufe `portals.light-level`, 12) und einer
  Nebelfläche (Item-Display).

Portale gibt es nur dort, wo sie registriert sind: in den Portal-Schreinen (unten) oder per Befehl. Pro
Welt stehen sie im Weltspeicher (`the_sift:portals`).

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

| Item | Weckt | Rezept (Standard, `portals.recipes`) | Beute (`portals.keys.*.loot`) |
|---|---|---|---|
| Echo-Resonator | Hauptwelt | 3 Echo-Splitter, Ziegenhorn, Sculk-Katalysator | Antike Stadt, 8 % |
| Seelenglut-Kern | Nether | Lohenrute, 2 Ghast-Tränen, Antiker Schrott, Seelensand | Bastionen, 8 % |
| Leere-Resonator | End | 3 Geplatzte Chorusfrüchte, Shulkerschale, Drachenatem | Endsiedlung (Schatz), 8 % |
| Sift-Kompass | – | Kompass, 4 Echo-Splitter, 4 Amethystsplitter | – |

- **Schlüssel:** Der falsche Schlüssel und schon geweckte Konsolen geben einen Hinweis.
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
  `void_resonator.png`
- `…/textures/item/sift_compass_00.png` … `sift_compass_31.png`: eine Nadelstellung je Bild, wie beim
  Vanilla-Kompass.
  - `16` zeigt geradeaus (nach oben), jedes weitere Bild 11,25° im Uhrzeigersinn weiter: `20` schräg
    rechts, `24` rechts, `00` unten, `08` links.
  - Das Skript schreibt auch die Modelle; die PNGs allein lassen sich jederzeit tauschen.

| Befehl | Wirkung |
|---|---|
| `/sift portal create <x y z>` | Rahmen aus Verstärktem Tiefenschiefer an dieser Stelle als Portal anlegen; Konsolen im Umkreis gehören dazu. Nicht in der Sift. |
| `/sift portal unlock [id]` | Konsolen wecken (Noten-Modus); danach öffnet die Melodie das Portal |
| `/sift portal activate [id]` | Sofort öffnen (bei offenem Portal: Fläche erneuern) |
| `/sift portal deactivate [id]` | Schließen; Konsolen bleiben wach. Das Haupt-Portal der Sift bleibt immer offen. |
| `/sift portal remove <id>` | Abmelden (Blöcke bleiben stehen) |
| `/sift portal info [id]`, `list` | Zustand, Rahmen, Konsolen; Liste aller Portale mit Teleport |
| `/sift portal locate` | Nächster Portal-Schrein der Welt (berechnet, auch ungeneriert) mit Teleport-Klick |

Ohne `id` wirkt der Befehl auf das nächste Portal im Umkreis von 64 Blöcken. Von der Konsole aus:
`/execute in minecraft:overworld run sift portal …`.

### Feuer und Brennstoff

Weidenstamm, -holz, -bretter, -laub und -setzling brennen wie im Original und taugen als Ofen-Brennstoff
(`blocks.<id>.fire`, `blocks.<id>.fuel`; Einzelheiten in `docs/ABWEICHUNGEN.md`). Die Gamerule
`fire_spread_radius_around_player` gilt auch dafür.

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
| `/sift tp` | In die Sift (zum Haupt-Portal) bzw. zurück in die Hauptwelt |
| `/sift items` | Item-Menü (auch mit `thesift.admin.items`) |
| `/sift give <spieler> <id> [anzahl]` | Sift-Block oder -Item geben (auch mit `thesift.admin.items`) |
| `/sift pack info`, `reload`, `send <spieler>` | Resource Pack anzeigen, neu laden, erneut senden |
| `/sift debug item <id>` | Komponenten eines Sift-Items anzeigen |
| `/sift debug break <x> <y> <z> <werkzeug> [none\|silk\|fortune1-3] [anzahl]` | Abbauzeit, Drops und XP eines Sift-Blocks berechnen, mit Statistik über viele Abbauvorgänge |
| `/sift debug find <id> [radius]` | Nächsten Sift-Block dieser Id suchen |
| `/sift debug worldgen` | Was die Weltgenerierung seit dem Start gesetzt hat (Weiden, Flecken, Sculk, verlassene Portale) und warum Portale ausfielen |
| `/sift debug portals` | Alle protokollierten verlassenen Portale: gebaute mit Teleport, abgelehnte mit Grund |
| `/sift debug landmarks [x z [zellen]]` | Kandidaten-Chunks der verlassenen Portale um eine Position (Standard 0/0, ±1 Zelle) mit Status, dazu die Mitten der Sculk-Regionen |
| `/sift debug heights <x> <z> <radius> <schritt>` | Höhenkarte der Sift als Text |
| `/sift debug scan [radius] [chunkX chunkZ]` | Chunks der Sift laden/generieren und Sift-Blöcke zählen |
| `/sift debug selftest` | Items, Rezepte, Siftit-Schutz, Loot und Advancements ohne Spieler prüfen |
| `/sift debug shape <x> <y> <z>` | Kollisionsboxen eines Blocks in der Sift (Pixel, Anteil am Würfel) |
| `/sift debug chorus <radius> <chunkX> <chunkZ>` | End-Chunks laden und zählen, ob Chorus dort Zustände der Sift hat (sollte 0 sein) |
