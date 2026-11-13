# The Sift – Bekannte Abweichungen vom Original

Was sich bewusst anders verhält als Mielon's The Sift 1.1.1, mit Grund. Optik und Texturen:
`docs/TEXTURE-COVERAGE.md`. Kleinere Verhaltensunterschiede der Blöcke: `docs/ADMIN.md`, Bekannte
Einschränkungen.

## Rezepte (seit alpha.4)

### Weggelassen (3 von 76)

| Rezept des Originals | Warum es fehlt | Ersatz |
|---|---|---|
| `chiseled_siftslate`: 2 Siftschieferziegelstufen → gemeißelter Siftschiefer (Werkbank) | Siftschieferziegelstufen gibt es hier nur als Tiefenschieferziegelstufen. Das Rezept wäre gleich dem Vanilla-Rezept für gemeißelten Tiefenschiefer und würde ihm Konkurrenz machen. | Steinsäge: Siftschiefer, Siftschiefer-Bruchstein oder Siftschieferziegel → gemeißelter Siftschiefer |
| `chiseled_siftslate_from_siftslate_brick_slab_stonecutting`: Siftschieferziegelstufe → gemeißelter Siftschiefer (Steinsäge) | wie oben | wie oben |
| `overgrown_willow_chest_boat`: Weidenboot + Truhe → Weiden-Truhenboot | Das Weidenboot ist hier ein Mangrovenboot; Vanilla macht daraus schon ein Mangroven-Truhenboot. | Vanilla-Rezept Mangroven-Truhenboot |

Der Test `RecipeSpecTest` hält genau diese drei fest; kommt ein weiteres dazu, schlägt er fehl.

### Hinzugefügt (3 + 42)

Im Original stehen Weidenbretter und -stämme in den Vanilla-Tags `#planks` und `#logs` und
funktionieren damit in allen Holzrezepten. Hier sind es Pilzblock-Items, die kein Vanilla-Tag erreicht.
Deshalb gibt es eigene Rezepte:

| Rezept | Datei |
|---|---|
| 2 Weidenbretter → 4 Stöcke | `plugin/src/main/resources/extra-recipes/overgrown_willow_sticks.json` |
| 4 Weidenbretter → Werkbank | `extra-recipes/crafting_table_from_overgrown_willow_planks.json` |
| Weidenstamm/-holz (auch entrindet) → Holzkohle (Ofen) | `extra-recipes/charcoal_from_overgrown_willow_logs.json` |
| 42 Holzrezepte mit Weidenbrettern bzw. -stämmen (seit alpha.24, #14) | `extra-recipes/willow_*.json` |

Die 42 Rezepte erzeugt `scripts/gen_willow_recipes.py` aus den Vanilla-Rezepten von 26.3: jedes Rezept mit
`#planks`, `#wooden_tool_materials` oder `#logs`, dazu `piston_from_cobbled_siftslate` des Originals.
Darunter sind Truhe, Fass, Bienenstock, Bücherregal und Gemeißeltes Bücherregal, Schüssel, alle Tische,
Notenblock, Jukebox, Kolben, Schleifstein, Haken, alle 16 Betten, Schild, die Holzwerkzeuge samt Speer,
Lagerfeuer, Seelenlagerfeuer und Räucherofen. Das Ergebnis ist das Vanilla-Item.

Unterschied zum Original: Ein Rezept nimmt entweder nur Weidenbretter oder nur Vanilla-Bretter;
gemischt geht es nicht. Bei einem neuen Minecraft-Update wird das Skript erneut ausgeführt.

### Ergebnis ist ein Vanilla-Item

Rezepte, deren Ergebnis hier nur als Vanilla-Block existiert, ergeben diesen: Siftschiefer-Treppen,
-Stufen und -Mauern → Tiefenschiefer-Varianten; Weidentür, -falltür, -zaun, -zauntor, -knopf,
-druckplatte, -schild, -hängeschild, -regal, -treppe, -stufe und -boot → Mangrove; Ichorglasscheibe →
lila Glasscheibe; Ichorschnee → Schnee.

**Ichorglasscheibe (#10, entschieden 03.11.):** Im Original 1.1.1 ein eigener Block (6 Ichorglas → 16
Scheiben, eigene Textur). Ohne Client-Mod gibt es keine freien Zustände für eine verbindende Scheibe.
Deshalb bleibt das Ergebnis die lila Vanilla-Glasscheibe mit Vanilla-Aussehen. Sie ist kein geschütztes
Sift-Item und verhält sich wie jede lila Scheibe.

## Sift-Items und ihre Vanilla-Basis

Sift-Items bauen auf Vanilla-Items auf (Papier, Netherit, Schneeball; Block-Items auf Faden, Glas oder
Pilzblöcken). Alle Sift-Items, seit alpha.6 auch die Block-Items (siehe `docs/ADMIN.md`, Schutz), verhalten
sich nicht wie ihre Basis. Im Original gibt es diese Frage nicht,
weil die Items eigene Item-Typen sind.

**Platzieren ohne Aufblitzen (alpha.42g):**
- **Bisher:** Der Client sagt beim Setzen den Block des Basis-Items in dessen Standardzustand voraus, also einen
  Vanilla-Pilzblock mit allen Seiten. Bis der Server den Sift-Zustand schickte, blitzte er kurz auf.
- **Jetzt:** Jedes Sift-Block-Item trägt die Komponente `minecraft:block_state` mit genau den Eigenschaften seines
  Sift-Zustands. Damit sagt der Client gleich den richtigen Block voraus.
  - Die Paper-API kann diese Komponente in 26.3 noch nicht bauen (`BlockItemDataProperties` ohne Setter). Das Plugin
    lässt sie deshalb vom Item-Parser des Servers erzeugen, wie bei `/give` – kein NMS.
  - Alle Wege, auf denen ein Sift-Block-Item entsteht, laufen über dieselbe Stelle: Drops, Crafting, Truhen-Loot,
    `/sift give`, das Item-Menü und die Boss-Beute.
- **Ausnahmen:**
  - **Ichorglas:** Chorus braucht Endstein, deshalb setzt das Item Glas. Es blitzt kurz als Glas auf, nicht als Pilz.
  - **Weidenstämme:** Sie sagen die stehende Achse voraus; liegend gesetzt dreht der Server sie im selben Tick.
  - **Ältere Items:** Items von vor alpha.42g haben die Komponente noch nicht. Der Spieler-Reset vor beta.1 entfernt
    sie ohnehin.

## Weidenholz brennt (seit alpha.5)

Im Original stehen die Weidenblöcke im `FlammableBlockRegistry` und in den Vanilla-Tags `#logs_that_burn`,
`#planks` und `#saplings`. Das Plugin bildet beides nach:

| Block | Entzünden / Verbrennen (wie Original) | Brennstoff |
|---|---|---|
| Weidenstamm, -holz (auch entrindet) | 5 / 5 | 300 Ticks (1,5 Gegenstände) |
| Weidenbretter | 5 / 20 | 300 Ticks |
| Weidenlaub | 30 / 60 | – |
| Weidensetzling | 60 / 100 | 100 Ticks |

Abweichungen, die bleiben:
- Das Feuer neben Weidenblöcken rechnet das Plugin (gleiche Formeln wie Vanilla, Zeittakt 30–39 Ticks).
  Lava entzündet Weidenblöcke nicht, Blitze nur über das entstehende Feuer.
- In den Brennstoff-Slot kommen Weidenblöcke per Klick und Shift-Klick, nicht per Trichter und nicht per
  Zahlentaste.
- Die Werte stehen in `config.yml` (`blocks.<id>.fire`, `blocks.<id>.fuel`).

## Weltgenerierung (seit alpha.5)

Die Java-Features des Originals laufen als Plugin-Populator nach den Datapack-Features:
Weidenbäume, Blumen- und Pflanzenflecken, Oberflächen-Sculk, verlassene Portale und das Haupt-Portal.

- Die Reihenfolge innerhalb eines Chunks ist wie im Original, aber alle fünf laufen nach den
  Datapack-Features, nicht dazwischen. Geprüft (alpha.6): Die einzigen Datapack-Features, die im Original
  erst nach den verlassenen Portalen kommen, sind die kleinen Pflanzen (Halme, Sprossen, Wedel). Sie
  stehen nicht in der Höhenkarte `WORLD_SURFACE_WG`, die Portale und Weiden zum Messen nehmen, und
  Portale und Bäume dürfen sie überschreiben. Die Reihenfolge ändert also nichts an Ort oder Annahme.
- Verlassene Portale folgen seit alpha.6 dem Original 1.1.1 (alpha.5 folgte noch dem Quelltext 1.0.2):
  ein Kandidat je 20 × 20 Chunks im mittleren Drittel der Zelle, Vorlage auf die Chunkmitte zentriert,
  die Drehung mit dem geringsten Höhenunterschied (höchstens 18), Fundament bis 12 Blöcke, Erdarbeiten
  höchstens 8 Blöcke. Vergleich mit dem Original auf demselben Seed (`lamakingdom`), 49 Kandidaten:

  | | gebaut |
  |---|---|
  | Original 1.1.1, ohne die Phase-3-Features | 21 |
  | Plugin (alpha.6) | 24 (20 davon dieselben Chunks) |
  | Original 1.1.1, vollständig | 6 |

  Die übrigen Unterschiede ohne Phase 3 kommen vom Zufall (Vorlage, Spiegelung, erste Drehung) und davon,
  dass das Plugin das Haupt-Portal schon beim ersten Start baut. Im vollständigen Original blockieren
  Seelenschluchten, Ichor-Seen, Lavaböden, Monolithen und Dornen viele Plätze (sie reservieren ihre Fläche
  vorher). Deshalb durfte bis alpha.27 nur ein Viertel der Kandidaten bauen (`chance: 0.25`). Seit alpha.28
  gilt wie im Original 1.0: Nachgemessen auf denselben Chunks wie das Original (Seed `lamakingdom`, 52 Kandidaten
  außerhalb der Startsperre ±200) bauen das Original und das Plugin je 8 (15 %), davon 4 an denselben Stellen.
- Das Haupt-Portal wird beim ersten Start gebaut, nachdem die Chunks um 0/0 generiert sind (das Original
  baut es beim Serverstart vor der Generierung). Bäume und Pflanzen auf dem Bauplatz werden dabei entfernt;
  verlassene Portale halten sich vom Suchgebiet fern.
- Die Dunklen Schnüffler in Sculk-Regionen kommen mit den Mobs (Phase 2).
- **Trockene Sculk-Spitzen, Bewuchs-Aufräumen und Lavaboden (seit alpha.30)** baut das Plugin beim Generieren,
  als Port von SiftDrySpikeFeature, CoveredGrowthCleanupFeature und SiftLavaFloorFeature aus 1.1.1 mit denselben
  Werten (Spitzen 1/21 überwuchert, 1/16 Ödland, Reservierung ±18; Aufräumen 56 Blöcke unter der Oberfläche;
  Lava bis y 11). Damit sind alle Java-Weltgenerierungs-Features des Originals übernommen. Unterschiede:
  - „Voller Block über dem Bewuchs“ prüft das Plugin über die Paper-API (Ober- und Unterseite tragfähig, fest)
    statt über die Kollisionsform; bei Vanilla-Vollblöcken und allen Sift-Blöcken ist das dasselbe.
  - Der Zufall der Spitzen ist der des Plugin-Populators (andere Plätze, gleiche Häufigkeit).
  - Das Aufräumen prüft standardmäßig 16 statt 56 Blöcke unter der Oberfläche
    (`covered-growth-cleanup.depth`). Über die Paper-API kostet das Lesen jedes Blocks deutlich mehr als im
    Original; bei 56 wären es etwa 8 ms je neuem Chunk. Bedeckter Bewuchs liegt praktisch nur direkt unter der
    Oberfläche (bei 8 Blöcken dieselbe Zahl an Funden je Chunk wie bei 56).
- **Schnüffler-Höhlen (seit alpha.28)** baut das Plugin beim Generieren, als Port von SnifferCaveFeature aus 1.1.1
  mit denselben Zellen (überwuchert 16 Chunks mit 4 Kandidaten, Ödland 28 mit 3), Formen, Blöcken, Pflanzen,
  Nest und Familie (2 Erwachsene, zu 33 % ein Junges; das Plugin erzeugt sie wie das Original erst, wenn die
  Chunks geladen sind). Dazu der Schnüffler-Pflanzenfleck (1/14). Unterschiede:
  - Das Ichor-Becken leuchtet über Lichtblöcke wie die Seen (Schalter `ichor-light`; seit alpha.36 über dem Ichor,
    nicht darin).
  - Der Zufall ist der des Plugin-Populators; die Kandidaten-Chunks sind dieselben wie im Original, ob dort
    gebaut wird, kann abweichen.
  - Viele Kandidaten scheitern an Erz, Siftschiefer-Bruchstein oder Geröll im Höhlenbereich
    („fremder Block im Weg“). Das ist im Original genauso: Als Sift-Fels zählen dort nur Siftschiefer, Bewuchs
    und Sculk, Erze und Blobs entstehen vor den Höhlen (alpha.30 geprüft; Original 10 Höhlen auf 111 Kandidaten).
- **Fackellilien, Kannenpflanzen und Blumen auf Sift-Boden (seit alpha.28):** wie im Original (dort per Mixin)
  bleiben sie auf Siftschiefer, Bewuchs, Sculk und Weidenlaub stehen. Selbst pflanzen lassen sie sich dort
  weiterhin nicht (Vanilla-Regel des Clients).
- **Monolithen und Bögen (seit alpha.27)** baut das Plugin beim Generieren, als Port von SiftMonolithFeature und
  SiftArchFeature aus 1.1.1 mit denselben Werten (Häufigkeiten, Biome, Radien, Höhen, Terrassen, Nebensäulen,
  Bogen-Suche über 4 Achsen, Kurve, Einkerbungen, Bewuchs). Unterschiede:
  - Der Zufall ist der des Plugin-Populators, nicht der Feature-Seed von Vanilla; dieselben Orte wie im
    Original entstehen daher nicht, die Häufigkeit ist dieselbe.
  - Solange das Haupt-Portal noch nicht steht, gilt das ganze Suchgebiet (±200 Blöcke um 0/0) als Portal
    (wie bei Seen und verlassenen Portalen); dort entstehen anfangs keine Monolithen und Bögen.
- **Ichor-Schnee (seit alpha.26)** wie im Original 1.1.1: in `ichor_snowy_peaks` auf jedem natürlichen Boden
  1–2 Schichten (gleiches Rauschen), kleine Gipfelpflanzen werden ersetzt, auch auf Weidenkronen, und
  Siftschiefer-Bewuchs darunter wird verschneit. Unterschiede:
  - Ichor-Schnee ist Vanilla-Schnee.
  - Schneefall: siehe „Himmel und Atmosphäre“ → Wetter (seit alpha.32).
- **Advancement „False Alarm“ (seit alpha.26):** wie im Original. Ein geworfener Ichor-Schneeball trifft etwas
  in 16 Blöcken eines lebenden Wärters.
- **Ichor-Seen und -Quellen (seit alpha.25)** baut das Plugin beim Generieren statt der Datapack-Features.
  Übernommen aus dem Original 1.1.1:
  - Häufigkeit (1/3, Lichtungen 1/2), die Varianten SMALL bis MEGA nach dem Gelände, Strand und Uferglättung,
    Becken- und Uferblöcke, Felsformationen in großen Seen;
  - die Quellen (16 Versuche je Chunk, Felswand mit genau einer Öffnung).

  - seit alpha.25.1 auch **Kaskaden** (an einem Steilhang: unterer See, Teich auf einem Bergsims, Abfluss
    mit fallendem Ichor) und **Weideninseln** in großen überwucherten Seen (27 %, mit einer kleinen Weide).

  Unterschiede:
  - Am Ufer wachsen kleine Pflanzen wie im Original, die Blumengruppen fehlen.
  - Rund um 0/0 (etwa ±200 Blöcke) entstehen beim ersten Start keine Seen: Bis das Haupt-Portal steht, ist dort
    der ganze Suchbereich reserviert. Das Original baut das Portal vor der Generierung und reserviert nur seine
    Fläche + 15 Blöcke; das Plugin macht das ab dann ebenso.
  - Größen: Auch im Original sind große Seen selten. In 12 Regionen (~1700 Chunks) des Originals gibt es 10 flache
    Seen: 8 SMALL, 2 MEDIUM, kein LARGE oder MEGA. Die Geländeschwellen sind dieselben.
  - Die Form kleiner Seen (Radius 4–7) wirkt eckig; das Original nutzt dieselbe Formel und sieht genauso aus.
  - Ichor ist Wasser; das Leuchten (Stufe 12) kommt von Lichtblöcken. Bis alpha.35 waren sie wassergeloggt und
    saßen im Ichor (jeder dritte Block der Oberfläche und jede Quelle). Ein wassergeloggter Lichtblock ist aber
    eine unsichtbare Wasserquelle: Er fließt dauerhaft weiter, auch wenn die sichtbare Quelle daneben weg ist,
    und sah im Spieltest (alpha.33) aus wie „neu entstehende Quellen“.
  - **Seit alpha.36** sitzt das Licht in der Luft **über** dem Ichor: bei Seen und Sniffer-Becken direkt über
    jedem dritten Block der Oberfläche, bei Quellen über der Öffnung in der Felswand. Die Quelle selbst ist
    normales Ichor (Quellblock). So gibt es keine versteckten Quellen mehr; nimmt man eine Quelle weg, läuft
    das fließende Ichor vollständig ab (Smoke-Test).
  - Unterschied zum Original: Dort leuchtet das Ichor selbst (eigene Flüssigkeit mit Licht 12). Hier hängt das
    Licht einen Block höher; die Helligkeit an der Oberfläche ist fast gleich, unter der Oberfläche ist es eine
    Stufe dunkler. Wird ein See abgepumpt, bleiben die Lichtblöcke über der leeren Stelle (unsichtbar, nur
    Licht). Wer mit einem Eimer Ichor genau in einen solchen Lichtblock gießt, macht daraus eine sichtbare,
    mit dem Eimer wieder aufnehmbare Quelle; dann geht das Licht beim Aufnehmen mit.
  - Chunks, die vor alpha.36 generiert wurden, behalten ihre wassergeloggten Lichtblöcke (Welt-Reset vor dem
    Go-live, siehe GOLIVE.md). `/sift debug ichor scan` zählt sie.
  - Vergleich, gleicher Seed und dieselben 166 Chunks: Original 1 See (69 Spalten), Plugin 1 See (125 Spalten).
    Größe und Form unterscheiden sich, weil der Zufall nicht derselbe ist.
- **Seelenschluchten (seit alpha.18)** baut das Plugin beim Generieren statt des Datapack-Features (Form,
  Rampe, Seelen-Adern und Markierungen wie im Original 1.1.1, gleiche Zellen und gleiches Salz). Unterschiede:
  - Der Zufall kommt vom Plugin, also andere Form und Richtung als im Original an derselben Stelle.
  - Ein Platz für Schluchten wird mit den verlassenen Portalen geteilt (keine Überschneidung); im Original haben
    Schluchten eine eigene Liste.
  - Seelenblöcke leuchten weiterhin nicht (bekannte Einschränkung, siehe ADMIN.md).
  - Die aufsteigenden Partikel sind Vanilla-Partikel (Standard `SCULK_SOUL`) statt des eigenen `canyon_soul`.
    Weil Vanilla-Partikel kleiner und kurzlebiger sind, kommen mehr davon (3 je Seelenblock, 4 Seelenblöcke je
    Schlucht alle 10 Ticks statt einer Schlucht reihum alle 2 Ticks), und „offen“ heißt 4 statt 50 freie Blöcke
    darüber (seit alpha.18.1, einstellbar).
  - Neu: `/sift debug canyon build` baut eine Schlucht in schon generiertem Gelände (Test).

## Portale (seit Schritt 7a)

Die Mechanik folgt dem Original 1.1.1:
- Rahmen aus Verstärktem Tiefenschiefer (3–23 Blöcke).
- Konsolen aus Klingendem Tiefenschiefer mit Notenblöcken; Rechtsklick wählt einen der 8 Klänge, Schlagen
  spielt ihn.
- Beim Öffnen Farbstoß, Nachspielen und eine Fläche, die sich vom Rand zur Mitte füllt (im Original nach der
  Melodie 1-3-7-6-5-2-4-8, hier seit alpha.37 nach dem Schlüssel, siehe unten).
- Durchgang zum Haupt-Portal der Sift und zurück zum Rückkehrpunkt; Transit-Schutz nach jeder Reise.

Unterschiede:
- **Öffnen nur mit dem Schlüssel (seit alpha.37, bewusste Abweichung, Entscheidung von Karl vor dem Go-live):**
  - **Original:** Ein Ziegenhorn ruft den Singer, dessen Gesang die Konsolen vom Horn- in den Noten-Modus
    schaltet; danach öffnet eine für alle Portale gleiche Melodie (1-3-7-6-5-2-4-8) das Portal.
  - **Hier:** Rechtsklick mit dem passenden Schlüssel auf eine Konsole. Die Konsolen erwachen nacheinander,
    der Auslöse-Klang ertönt, die Konsolen spielen ihre Klänge mit Lichtstrahlen nach und die Fläche wächst.
    Ablauf, Klang und Bild bleiben, nur der Auslöser ist ein anderer. Der Schlüssel wird verbraucht
    (`portals.keys.consume`).
  - **Gründe:** Eine Melodie lässt sich ohne Client-Mod kaum vermitteln (das Original hat dafür eigene Hinweise
    und Partikel). Ein Geheimnis im öffentlichen Repo schützt nicht (die Melodie der Antiken Stadt stand in der
    `config.yml`). Die Schlüssel geben eine klare Staffelung je Dimension: Hauptwelt (Schwerer Kern) < Nether
    (Wither-Schädel, Netherit) < End (Drachenkopf, Elytren). Schreine in allen Dimensionen, Schlüssel und eigene
    Melodien je Portal waren schon vorher Abweichungen.
  - **Singer:** Das Ziegenhorn ruft ihn weiter, und er singt wie im Original. Seine Schallwellen lassen die
    Konsolen nur noch aufleuchten, statt sie zu wecken.
  - **Antike Stadt:** eigener Schlüssel (Antiker Resonator, nur 1 % in den Truhen der Stadt, kein Rezept) statt
    der geheimen langen Melodie aus dem Buch.
- **Nur registrierte Portale:** Im Original zündet die Melodie jeden passenden Rahmen im Umkreis von
  10 Blöcken. Hier nur Portale aus Strukturen oder per `/sift portal create`. Die Konsolen eines Portals
  stehen in seinem Eintrag; eine Konsolengruppe über 16 Blöcke wie im Original wird nicht gesucht.
- **Schließen:** Im Original schließt die rückwärts gespielte Melodie das Portal. Hier bleibt es dauerhaft offen
  (Entscheidung vom 30.10.), nur Admins schließen es.
- **Aussehen:**
  - Die Portalfläche des Originals rendert ein Client-Shader. Hier ist sie eine halbtransparente
    Nebelfläche aus der Original-Textur (ein Item-Display) über unsichtbaren Lichtblöcken (Stufe 12).
  - Die farbigen Notenpartikel und Lichtstrahlen sind Staubpartikel in den Farben des Originals.
- **Reittiere:** Reiter samt Reittier reisen nicht mit.
- **Andere Dimensionen:** Im Original führen nur Portale der Hauptwelt in die Sift. Hier ist jeder Eingang
  außerhalb der Sift einer, also auch die Portale in Nether und End aus Schritt 7b.
- **Fundorte (seit 7b):**
  - Im Original liegt das Portal im umgebauten Zentrum der Antiken Stadt, und den Weg dorthin zeigt
    nichts.
  - Hier gibt es eigene Schreine in Hauptwelt, Nether und End. Sie sind Ersatz für das Zentrum der
    Antiken Stadt, bis Phase 2 die Melodie dort als Option bringt.
  - Der Sift-Kompass zeigt den Weg. Die Schlüssel ersetzen Singer und Melodie.
  - Schreine, Schlüssel und Kompass gibt es im Original nicht; ihre Texturen sind eigene.
- **Melodie je Portal (alpha.20 bis alpha.36):** Jeder Schrein hatte eine eigene, zufällige Melodie. Seit
  alpha.37 entfällt die Melodie ganz (siehe „Öffnen nur mit dem Schlüssel“).

## Ichor (seit alpha.21)

Im Original ist Ichor eine eigene Flüssigkeit (eigene Texturen, Lichtstufe 12, fließt langsamer und nur
etwa 6 Blöcke weit). Ohne Client-Mod ist Ichor Vanilla-Wasser in der Sift mit Ichor-Biomfarbe. Das Verhalten
wird nachgebildet:
- Regeneration I wie im Original.
- Bremsung nur für Mobs und Items: Spieler würden bei einer Bremsung durch den Server ruckeln.
- Keine unendlichen Quellen, Lava ergibt Siftschiefer bzw. Bruchstein, Eimer und Flasche, Kessel.
- Nicht nachgebildet:
  - das eigene Fließtempo und die geringere Reichweite;
  - das Leuchten (Generierte Seen und Quellen bekommen Licht ab alpha.25);
  - „ichorlogged“-Blöcke (wassergeloggte Blöcke sind normales Wasser);
  - die eigene Luftanzeige;
  - die Blasen- und Spritz-Partikel (ersetzt durch Staub-Nebel über Quellen);
  - das Leuchten des Kessels;
  - Tropfstein füllt den Kessel nur wie bei Vanilla (nicht aus 13 Blöcken Höhe).
- **Ichor bleibt Ichor, Wasser bleibt Wasser (seit alpha.38, Entscheidung von Karl):** Im Original sind Ichor und
  Wasser zwei Flüssigkeiten. Hier sind beide Vanilla-Wasser; welche Art ein Block hat, entscheidet
  „in der Sift Ichor, sonst Wasser“, und nur Ausnahmen werden je Block in den Chunk-Daten markiert
  (Wassereimer in der Sift, Ichoreimer außerhalb). Fließendes Wasser erbt die Art seines Ursprungs. Eimer,
  Werfer, Flaschen, Kessel, Regeneration und Bremsung richten sich danach.
  - **Aussehen:** Die Farbe kommt vom Biom und lässt sich nicht je Block ändern: Wasser in der Sift sieht lila
    aus, Ichor in der Oberwelt blau.
  - **Grenzen:** `/fill`, `/setblock`, WorldEdit und andere Plugins setzen Wasser ohne Markierung (es gilt dann
    der Standard der Welt). Treffen zwei Ströme verschiedener Art zusammen, bestimmt der zuletzt fließende die
    Art. Kolben verschieben kein Wasser; ein geschobener wassergeloggter Block verliert in 26.3 sein Wasser
    (geprüft). Lava-Ergebnisse, Nebel und die Sniffer-Regel gelten nur in der Sift.
  - Ichor und Wasser mischen sich im Kessel nicht: Eine Flasche der anderen Art wird abgewiesen.

## Phase 4: eigene Inhalte (seit alpha.39)

Nicht im Original. Karls eigene Ideen nach dem Port, alle unter `phase4.*` abschaltbar:
- **Boss-Items** (alpha.39): Herz des Wächters, Kern des Withers, Drachenschuppe aus den Vanilla-Bossen, dazu die
  Nachtkristall (`golem_trophy`, bis alpha.41.1 Arbeitstitel „Golem-Trophäe“) für den späteren eigenen Boss. Die Texturen sind eigene (Skript), keine Mojang-Dateien.
- **Sculk Enchanter** (alpha.40, eigener Block seit alpha.40.1): Ritual mit dem Charoit-Kern an einem Zaubertisch. Er
  hebt Verzauberungen bis 2 Stufen über das Vanilla-Maximum. Der Block hat eine eigene Optik (Charoit-Rune,
  seelenblaue Gravur, schwebender Kristall), aber nur für Java mit Resource Pack: **Bedrock-Spieler (Geyser) sehen
  einen roten Pilzblock**, wie bei allen Sift-Blöcken. Die Funktion (Ritual, Menü, Abbau) bleibt für alle gleich.
- **3D-Item-Modelle** (alpha.40.2): Boss-Items, Charoit-Kern und der schwebende Kristall sind Element-Modelle
  (eigene Formen und Materialtexturen, keine Mojang-Dateien). Bedrock-Spieler (Geyser) sehen sie nicht, sie
  bekommen die Basis-Items (Papier).
- **Tempel** (alpha.41a, Bastion über einer Prüfungsebene seit alpha.41.1): genau 10 je Sift auf einem Ring um 0/0, im
  Plugin gebaut (keine Datapack-Struktur, damit die Zahl fest ist und die Plätze vorab bekannt sind). Am Seelen-Altar
  erwacht seit alpha.42b.1 der Boss, die Prüfungsräume für Wither und Wächter bleiben bis zu ihren Prüfungen versiegelt. Tempel
  entstehen nur in neu generierten Chunks.
  - Ein Tempel reicht über 7 × 7 Chunks. Chunks, die vor der Tempelmitte generiert werden, bekommen ihren Teil kurz
    danach auf dem Main-Thread (die Paper-API kennt die Geländehöhe eines noch nicht generierten Chunks nicht, NMS wäre
    der einzige andere Weg).
  - Hofboden, Treppen, Stufen, Mauern, Gitter, Ketten und Laternen sind Vanilla-Tiefenschiefer bzw. Vanilla-Blöcke: Auf
    Siftschiefer (Pilzstiel-Zustände) würden die Pflanzen der Sift-Weltgenerierung wachsen.
  - In den Chunks eines Tempels setzt der Sift-Populator keine Bäume, Pflanzen, Sculk-Flecken oder Quellen.
- **Erfahrung** (alpha.42h):
  - Brut-Mobs der Tempel geben feste Erfahrung je Art (Sifter 5, Schnüffler 10, Irrlicht 8), aber keine Beute.
  - Das Charoit-Irrlicht gibt auch außerhalb der Brut 8.
  - Der Boss verteilt 500 Erfahrung auf die Berechtigten.
  - Der Dunkle Schnüffler behält als normaler Mob die 1–3 des Originals.
- **Advancements** (alpha.42h):
  - „Riss-Resonanz“: Die Rift-Schallplatte zählt in jedem Plattenspieler der Sift, nicht nur in 16 Blöcken um einen Riss.
    Die Risse sind auf Lama Kingdom aus.
  - „Rissgänger“ bleibt ohne Risse unerreichbar (Entscheidung offen).
  - „Das Dunkel pflanzen“ bleibt unerreichbar, solange der Anbau der Sculkblume fehlt.
- **Ein Altar je Spieler** (alpha.42f): Jeder Spieler darf den Großen Echo-Golem nur an einem Tempel wecken (Bindung an
  den Auslöser, Mitkämpfer frei, abschaltbar). Eigene Regel, nicht im Original. Gespeichert in den Weltdaten der Sift
  statt im Spieler, damit Admins sie auch für Offline-Spieler sehen und aufheben können.
- **Tempel-Wege** (alpha.42e, Layout-Version 4): Alle Wege, Räume und Spawner-Zonen sind für Mobs bis 3 × 3 × 3 gebaut
  (Zone 3 4 hoch), weil die Sift-Mobs breiter als Spieler sind (Dunkler Schnüffler 1,9 × 1,9) und in 1 breiten Gängen
  erstickten. Die Hitbox der Mobs bleibt, wie sie ist (keine Verkleinerung per Config). Weniger Treppen auf den Wehrgang
  (zwei statt vier, dafür 3 breit). Die Mauerhöhe ist auf `size.radius` − 19 begrenzt, damit die Wandtreppe zwischen die
  Türme passt. Bereits gebaute Tempel ändern sich nur mit `/sift temple rebuild`.
- **Charoit-Irrlicht** (alpha.42d): eigener Mob für Zone 3 der Brut-Spawner, nicht im Original; eigenes Modell und
  eigene Textur (Skript), Basis unsichtbare Biene; nur aus den Spawnern.
- **Brut-Spawner** (alpha.42a): 12 je Tempel, vom Plugin gesteuert (Wellen, Skalierung, Erschöpfung wie ein
  Prüfungsspawner). Optik: Vanilla-Prüfungsspawner ohne Vanilla-Logik; wach blau (ominös), erschöpft dunkel; die
  Vanilla-Zustände „aktiv“ und „Abklingzeit“ lassen sich nicht halten (Vanilla setzt sie ohne Mob-Daten zurück).
- **Siftit-Block und Charoit-Block** (alpha.41.1): eigene Speicher- und Zierblöcke, nicht im Original.
- **Tempel-Kompass und Gerüchte-Zettel** (alpha.41b): Der Sift-Kompass zeigt in der Sift wahlweise zum nächsten
  schlafenden Tempel; Zettel in Truhen nennen Richtung und gerundete Entfernung. Nicht im Original.
- **Großer Echo-Golem** (alpha.42b.1): eigener Boss, nicht im Original. Er nutzt das Modell des Echo Golems
  (2,5-fach, gefärbt) mit Seelenblöcken, Charoit-Blöcken und einem Nachtkristall als zusätzliche Modellteile; Bewegungen
  aus den vorhandenen Golem-Animationen (Aufheben = Faustschlag, Verbeugen = Stampfer, Tragen = Schall aufladen). Ein
  eigenes Blockbench-Modell kann es später ersetzen. Seine Lebenspunkte sind ein eigener Pool des Kampfes (Vanilla
  begrenzt Lebenspunkte auf 1024); die Bossleiste ist eine Plugin-Bossleiste. Das Seelengitter ist ein Kraftfeld ohne
  Blöcke (Partikel in den Toren, Zurücksetzen statt Wand). Bedrock-Spieler (Geyser) sehen den Golem nicht, nur die
  Bossleiste und die Angriffe (wie bei allen Sift-Mobs). Seit alpha.42b.2 mit eigener Netherit-Beute (Tabelle in der
  Config) und dem eigenen Advancement „Stille im Tempel“ (nicht im Original; seit alpha.42c auf Deutsch und Englisch).
- **Sprachen** (alpha.42c): Das Original hat Englisch, Polnisch und Koreanisch, unsere eigenen Inhalte gibt es auf
  Deutsch und Englisch. Ersatztexte (ohne Pack) sind seit alpha.42c englisch. Meldungen des Plugins kommen in der
  Sprache des Clients (Deutsch für `de_*`, sonst Englisch), unabhängig vom Pack.

## Siftit-Erhalt beim Tod (seit alpha.23)

Ablauf, Zeiten und Flugbahn wie im Original 1.1.1. Unterschiede:
- **Aussehen:** Der Mini-Riss ist ein leuchtender Würfel (Display mit der Sift-Vorschau-Textur). Die
  zurückfliegenden Gegenstände sind Item-Displays.
- **Fluch des Verschwindens:** Im Original bleiben auch solche Siftit-Gegenstände erhalten. Hier entfernt
  Paper sie vor dem Ereignis aus den Drops; sie bleiben verloren.
- **Neu:** `/sift siftite list|restore` als Notausgang für Admins.

## Risse (seit alpha.22)

**Seit alpha.42a.2 standardmäßig aus** (`rifts.enabled: false`, Karls Wunsch: keine Abkürzung in die Sift, Zugang nur
über die Portale mit Schlüssel). Im Original gibt es sie immer. Der Mini-Riss beim Siftit-Erhalt bleibt. Mit
`rifts.enabled: true` öffnen sich Gegen-Risse nur, solange die Sift offen ist.

**Gestaffelte Portale** (alpha.42a.2, nicht im Original): End-Schreine und Antike Städte ab Tag 0, Nether-Schreine ab Tag
7, übrige Oberwelt-Schreine ab Tag 14 nach der Öffnung der Sift (`portals.unlock-schedule`).

Verhalten, Zeiten, Abstände, Licht und Klänge wie im Original 1.1.1. Unterschiede:
- **Aussehen:** Das Original zeichnet den Riss mit einem eigenen Shader (fließende Energie, Vorschau des
  Ziels, die beim Näherkommen erscheint). Hier ist er eine leuchtende Fläche aus einem Display.
  - Am Rand die Energie-Textur, in der Mitte das Vorschaubild des Ziels (Sift bzw. Oberwelt, Texturen des
    Originals).
  - Öffnen und Schließen sind als Skalierung animiert.
- **Kein Ladebildschirm:** Der Client zeigt den normalen Dimensionswechsel.
- **Neustart:** Offene Risse werden nicht gespeichert und sind nach einem Neustart weg (im Original bleiben
  sie). Ihre Lichtblöcke werden sicher entfernt.
- **Mob-Meidung:** Das Original verteuert die Wegfindung um Risse. Hier werden Pfade, die hineinführen,
  abgebrochen (Paper hat keine Pfadkosten je Fläche).
- **Gegen-Riss:**
  - Er entsteht nur für Spieler; Mobs und Items, die hinausgehen, landen am Welt-Spawn.
  - Er sucht nicht im Spawn-Schutz und nicht dort, wo der Spieler nicht bauen darf (Claims).
- **Zeitsperre:** Risse in die Sift beachten die Zeitsperre aus alpha.20 (neu).
- **Item `sift_rift`:** wie im Original nur im Kreativmodus, zusätzlich für Admins.

## Himmel und Atmosphäre (seit alpha.29)

Was ohne Client-Mod geht, ist übernommen; der Rest braucht den Client des Originals.

**Wie im Original:**
- Farben von Himmel, Nebel und Wasser, die Tag-und-Nacht-Zeitlinie, Umgebungsklänge, Musik und die
  Schallplatten kommen 1:1 aus dem Datapack und dem Resource Pack des Originals.
- **Intro-Klang:** `the_sift:sift_intro` spielt einmal je Spieler beim ersten Aufenthalt in der Sift (Umgebung,
  Lautstärke 0,4). Das Original startet ihn, wenn der Ladebildschirm fertig ist; hier, sobald der Spieler
  20 Ticks in der Sift ist und Boden in 8 Blöcken unter sich hat. Gemerkt wird das im Spieler
  (`the_sift:intro_played`), es übersteht Neustarts; Spieler, die schon vor alpha.29 in der Sift waren, hören
  es einmal beim nächsten Besuch. Schalter `atmosphere.intro-sound.*`.

**Anders, weil ein Vanilla-Client es nicht kann:**
- **Wolken:** Das Original zeigt Wolken nur bei Regen. Ein Vanilla-Client zeigt sie entweder immer oder nie
  (er zeichnet sie nur, wenn die Wolkenfarbe nicht ganz durchsichtig ist). Standard: aus
  (`atmosphere.clouds: false` macht die Wolkenfarbe im Datapack durchsichtig), mit `true` immer sichtbar.
- **Himmel:** Das Original zeichnet einen eigenen Polarlicht-Himmel ohne Sonne, Mond und Sterne (Client-Renderer).
  Hier bleibt der Vanilla-Himmel mit den Farben des Originals; Sonne, Mond und Sterne sind sichtbar.
- **Wetter (seit alpha.32):**
  - Wie im Original hat die Sift ihr eigenes Wetter. Paper führt das Wetter ohnehin je Welt, deshalb braucht
    es dafür nichts (das Original braucht einen Mixin). `/weather` in der Sift gilt nur dort.
  - **Ichor-Schneefall** wie im Original nur in `ichor_snowy_peaks`, dort in jeder Höhe, nur bei Blocklicht unter
    10, höchstens 2 Schichten; in allen anderen Biomen bleibt es Regen, auch auf hohen Gipfeln. Das Original
    tauscht dafür Vanillas Temperaturprüfung aus. Über Paper geht das nicht direkt (Vanilla bildet Schnee nur
    unter Temperatur 0,15, das Biom hat 0,18 und erreicht das erst ab etwa y 104). Deshalb setzt das Plugin beim
    Start die Temperatur des Bioms im Datapack auf 0,0 und prüft jeden gebildeten Schnee
    (`weather.ichor-snowfall.*`). Folgen:
    - Ein Vanilla-Client zeigt dort Schneefall statt Regen, auch unten (das Original zeigt unterhalb von y 104
      Regen, obwohl Schnee liegen bleibt).
    - Ichor-Schnee ist Vanilla-Schnee; leere Kessel füllen sich dort bei Schneefall mit Pulverschnee.
  - **Ichor friert nicht zu:** Im Original ist Ichor eine eigene Flüssigkeit. Hier ist er Wasser und würde im
    kalten Biom zu Eis werden; das Plugin verhindert das (`weather.ichor-freezes: false`).
- **Ichor-Regen und Ichor-Schnee:** eigene Wetter-Texturen im Original. Ein Resource Pack kann die Regen- und
  Schneetextur nur für alle Welten tauschen, deshalb bleibt sie Vanilla.
- **Ladebildschirm** beim Betreten (siehe Portale und Risse): der normale Dimensionswechsel.
- **Partikel `sift_note`** und die Nebel- und Blasen-Partikel des Ichors: Vanilla-Partikel als Ersatz
  (Noten, Staub in Ichorfarben).
- **Wolken-Shader** (`rendertype_ichor_clouds`) und weitere Shader: wirken in einem Resource Pack global und
  sind deshalb nicht übernommen.

## Zugang zur Sift (seit alpha.20)

Neu, im Original gibt es das nicht: Die Sift lässt sich zeitlich sperren (`access.mode`: immer, Tage nach
dem ersten Drachenkill, ab Datum oder nur per Befehl). Solange sie zu ist, kommt niemand ohne
`thesift.access.bypass` hinein, auch keine Mobs oder Items; Wecken und Öffnen der Portale gehen weiter.
Beim Schließen werden Spieler nach einem Countdown zurückgeschickt. Standard ist `always` (wie das Original).

## Mobs (seit Phase 2)

**Allays (seit alpha.31)** wie im Original (SiftAllaySpawner 1.1.1): Vanilla-Allays erscheinen in Gruppen nahe
Spielern im Überwucherten Wald (Chance 0,72, 2–4, höchstens 16 im Umkreis), im Sift-Ödland (0,5, 2–3, 12) und in
der Lichtung (0,32, 1–3, 8), alle 80 Ticks, 34–80 Blöcke entfernt, 6 Blöcke über dem Boden. Es sind echte
Vanilla-Allays, kein Sift-Mob mit eigenem Modell; sie erscheinen mit dem Spawn-Grund „Plugin“ statt „natürlich“.

Allgemein, für alle Sift-Mobs:
- **Darstellung:** Im Original zeichnet GeckoLib die Mobs auf dem Client. Hier trägt ein unsichtbarer
  Vanilla-Mob Hitbox, Wegfindung und Kampf; das Modell besteht aus Item-Displays (eines je Würfel des
  Original-Modells, Textur aus dem Original). Der Server rechnet die Animationen des Originals und sendet
  10 Posen pro Sekunde, der Client interpoliert dazwischen. Übergänge zwischen Animationen sind dadurch
  etwas weicher bzw. ungenauer als im Original.
- **Nur Java:** Bedrock-Spieler (Geyser) sehen die Sift-Mobs nicht.
- **Rotes Aufblinken und Tod:** Treffer färben das Modell kurz rot (Item-Tönung), beim Tod kippt es wie
  ein Vanilla-Mob. Der Schatten sitzt etwas höher und ist schwächer.
- **Klänge:** Der Basis-Mob ist stumm; die Klänge des Originals spielt das Plugin an der Position des
  Mobs ab (nicht an den Mob gebunden). Schwimm- und Platschklänge fehlen.
- **Namen:** Ein Blick auf den Mob zeigt keinen Namen; Todesnachrichten nennen den Sift-Namen.

Blub:
- **Basis:** unsichtbarer Wolf, Größe 1,1; Hitbox 0,66 × 0,94 statt 0,8 × 0,9.
- **Hüpfen:** Im Original bewegt eine eigene Steuerung (wie beim Kaninchen) den Blub nur in Sprüngen.
  Hier hält das Plugin die Laufgeschwindigkeit an Land auf null und lässt ihn alle paar Ticks zum nächsten
  Wegpunkt springen (Höhe 0,3 / 0,36 / 0,5 wie im Original, Vorwärtstempo `hopping.hop-velocity`). Das
  Tempo ist angenähert.
- **KI:** Statt eigener Goals behält der Wolf die Vanilla-Goals, die das Original auch nutzt (Schwimmen,
  Sitzen, Nahkampf, Folgen, Umherstreifen, Umschauen, Besitzer schützen); alle anderen Wolf-Goals sind
  entfernt. Das Locken mit Seelenblöcken, der Rudelzorn und das Zurückschlagen macht das Plugin.
- **Spawnen:** Im Original gibt es keinen natürlichen Spawn, nur das Spawn-Ei. Hier erscheint der Blub
  selten in den Überwucherten Biomen (Entscheidung vom 31.10., `mobs.blub.spawn`, abschaltbar).
- **Erfahrung:** 1–3 wie bei Vanilla-Tieren (`mobs.blub.experience`).
- **Mielon-Texturen:** Die Sonder-Texturen des Originals für Blubs namens „Mielon“ fehlen.

Sifter:
- **Ziele wie im Original:** jagt nur Spieler, schlägt aber jeden zurück, der ihn verletzt
  (`HurtByTargetGoal`). Ein gezähmter Blub, der seinen Spieler verteidigt, kann deshalb von einem Sifter getötet werden.
- **Basis:** unsichtbares Schwein; Hitbox 0,9 × 0,9 statt 0,9 × 0,95. Das Schwein bekommt das Attribut
  Angriffsschaden dazu.
- **KI:** Das Schwein behält nur Schwimmen, Umherstreifen und Umschauen. Zielwahl (nächster sichtbarer
  Spieler, wer ihn trifft), Verfolgen und Nahkampf sind Nachbauten der Vanilla-Goals im Plugin; der
  verzögerte Biss (14 Ticks, Klang Tick 8, Schaden Tick 13, Reichweite 2,5) folgt dem Original.
- **Spawner:** Wie SiftSifterSpawner (alle 80 Ticks, bis 18 Punkte auf einer Spirale in 32–76 Blöcken,
  80 % Oberfläche, Bodenblöcke, Profile, Gruppen 1–5). Unterschiede:
  - Hier bekommt jeder Spieler alle 80 Ticks einen eigenen Versuch; im Original gibt es je 80 Ticks
    höchstens eine Gruppe für den ganzen Server.
  - Die Grenze je Profil zählt Sifter in einer Kugel von 96 Blöcken statt in einem Kasten 96 × 48 × 96.
  - Zusätzlich gelten die gemeinsamen Grenzen aller Sift-Mobs (8 je Spieler-Umkreis, 150 je Welt).
- **Friedlich:** Im Original spawnt der Sifter dort nicht und greift nicht an; hier verschwindet er
  zusätzlich wie ein Vanilla-Monster.
- **Erfahrung:** 7 wie im Original, nur wenn ein Spieler ihn tötet (wie Vanilla). Das gilt jetzt für alle
  Sift-Mobs, auch für den Blub.

Singer:
- **Basis:** unsichtbarer Fahrender Händler, Größe 2,05 (Hitbox 1,23 × 4,0; Original 0,8 × 4,0 bei Größe 1,2,
  also 0,96 × 4,8). Der Plan sah einen Enderman vor. Er ginge aber nicht durch: Ein Enderman erzeugt auf dem
  Client immer lila Portalpartikel (auch unsichtbar), ist gegen Pfeile immun (er weicht aus) und als Monster
  verschwindet er auf Friedlich. Der Händler ist kein Monster und läuft ohne eigene KI; Zombies und
  Plünderer greifen ihn nicht an.
- **Gerufen:** wie das Original: 3 s nach dem Ziegenhorn, nächster Rahmen in 40 Blöcken, Erscheinen, Gesang
  mit 8 Schallwellen, Abtauchen. Unterschiede:
  - Seit alpha.37 wecken die Schallwellen die Konsolen nicht mehr, sie leuchten nur auf; das Portal öffnet
    allein der Schlüssel (siehe Portale).
  - Er kommt nur zu **schlafenden** registrierten Portalen (Schrein, Antike Stadt, eigene per Befehl).
  - Die Schallwellen sind Staubpartikel statt des eigenen Partikels; die Lichtsäule am Kopf (Client-Licht)
    fehlt.
  - Wird der Chunk während des Auftritts entladen oder der Server neu gestartet, ist der Singer danach weg.
- **Bleibend (Spawn-Ei, Befehl):** Im Original läuft er unbegrenzt umher. Hier bleibt er im Umkreis von
  8 Blöcken um seinen Erscheinungsort, nimmt keine Wege mit weniger als 4 Blöcken Höhe und taucht ab, wenn
  er 10 s festsitzt (Entscheidung Phase 2).
- **Seelen-Ereignis mit dem Echogolem** (seit alpha.19) wie im Original: Der Golem ruft einen Seelen-Singer, der den
  Block nimmt. Der Seelen-Singer meidet Schluchten nur beim Erscheinen (nicht 24 Blöcke an einer Schlucht), nicht
  mehr beim Herumlaufen; ein bleibender Singer (Spawn-Ei) nimmt Golem-Blöcke ebenfalls an.
- **Tausch:** Gewichtungstabelle des Originals (`mobs.singer.barter.table`). Den gehaltenen Seelenblock zeigt
  das Modell nicht (eigene Render-Schicht im Original); die leuchtenden Teile der Textur (Glowmask) leuchten
  nicht.

Antike Stadt:
- **Zentrum:** die drei `city_center`-Vorlagen des Originals (per Datapack, nur neue Chunks). Die Konsolen
  stehen darin als Leitstein-Platzhalter, weil Vanilla Vorlagen dreht; das Plugin setzt beim Generieren
  den Klingenden Tiefenschiefer und registriert das Portal, sobald Rahmen und alle 8 Konsolen da sind.
- **Notenblöcke:** Im Original stehen auf den Konsolen keine Notenblöcke (die Spieler bringen sie mit). Hier
  setzt das Plugin wie bei den Schreinen Notenblöcke mit zufälligen Klängen; Kerzen und andere nicht feste
  Deko darüber weichen.
- **Öffnen:** Im Original öffnet dieselbe Melodie wie überall. Hier öffnet seit alpha.37 nur der Antike
  Resonator (seltener Fund in den Truhen der Stadt). Die lange Melodie mit dem Buch (aus Phase 2, bis alpha.36)
  gibt es nicht mehr.

Echo Golem (seit alpha.19):
- **Basis:** unsichtbares Schaf, Größe 1,28 (Hitbox 1,15 × 1,66 statt 1,15 × 1,65), Stufenhöhe 1,5 wie im Original.
  Scheren, Färben, Grasfressen und Vermehren sind gesperrt.
- **Wegfindung vereinfacht:** gleiche Abschnitte (Erkunden, Annähern, Eintreten, Absteigen, Seele suchen, leere
  Schlucht verlassen, Hinausklettern, Abliefern), Zeiten, Reichweiten und Fehlergrenzen wie im Original; die
  Rückfall-Wege (anderer Ausgang, Notaufstieg, Zwischenziele) sind kürzer gefasst als die rund 2000 Zeilen des
  Originals. Teilwege, die näher heranführen, zählen nicht als Fehlschlag.
- **Schluchten ohne geladene Seelenblöcke** zählen wie im Original nicht als „aktiv“; der Golem geht also nur zu
  Schluchten in geladenen Chunks (in der Nähe von Spielern).
- **Mitte und Eingang** gehören seit alpha.19 fest zu ihrer Schlucht (im Original: der nächste Eingang in 96
  Blöcken; das bleibt der Rückfall für Schluchten aus alpha.18).
- **Aufheben** beachtet zusätzlich `pickup.enabled` und die Gamerule `mob_griefing` (im Original immer).
- **Leuchten:** Das Original leuchtet über einen Client-Mod (Licht 14) und eine leuchtende Textur. Hier: ein
  Lichtblock in Luft am Kopf (folgt bei Blockwechsel, sicher aufgeräumt) und ein selbst leuchtender
  Seelenblock-Würfel im Modell (`light_emission`).
- **Kopf:** Das Modell dreht sich als Ganzes; das getrennte Kopfdrehen (±55°) und das Hinschauen fehlen.
- **Übergabe an den gebundenen Spieler (alpha.19.2):** nur im Überlebens- oder Abenteuermodus (das Original gibt auch
  im Kreativmodus); bei vollem Inventar landet der Block vor den Füßen (nur der Spieler kann ihn aufheben) mit Hinweis,
  das Advancement kommt trotzdem. **Rechtsklick des Besitzers** (ohne Splitter) auf seinen tragenden Golem übergibt
  den Block sofort, statt ihn fallen zu lassen (`mobs.echo_golem.bonded.right-click-gives`, Standard an; `false` =
  Original). Treffer und Rechtsklicks auf ungebundene Golems lassen ihn weiter fallen.
- **Binden** nur über die UUID des Spielers (das Original stellt die Bindung auch über den Namen wieder her). Gespeichert
  wie im Original als `bonded_player` / `bonded_player_name` (PDC `the_sift:`). Beim Binden spielt zusätzlich ein Klang
  (Allay), weil die Herzen allein leicht zu übersehen sind.

Dunkler Schnüffler:
- **Modell:** Im Original ist er der Vanilla-Schnüffler mit eigener Textur. Das Mojang-Modell darf nicht ins
  öffentliche Repo; hier gibt es ein eigenes, vereinfachtes Modell aus 7 Würfeln (Körper, Kopf, Schnauze, vier
  Beine), abgeleitet aus der Hitbox. Die Flächen zeigen passende, deckende Bereiche der Original-Textur
  `dark_sniffer.png` (MIT). Animationen sind eigene: Gehen, Angriff, Schnüffeln, Graben.
- **Basis:** unsichtbarer Eisbär, Größe 1,36 (Hitbox 1,9 × 1,9 statt 1,9 × 1,75). Ein echter Schnüffler
  ginge nicht, weil sein Verhalten über ein „Brain“ läuft, das sich nicht abschalten lässt.
- **Schallangriff:** wie im Original: Er zieht Getroffene zum Schnüffler heran und macht keinen Schaden (der
  Plan nannte ihn „wie der Wächter“; gemeint ist die Optik). Die Welle zeigt Wächter-Partikel statt des eigenen
  Partikels des Originals.
- **Graben:** Das Schnüffeln und Graben des Vanilla-Schnüfflers (Brain) ist nachgebaut: ohne Ziel sucht er
  ab und zu (alle 10 s) Sculk in 8 Blöcken, gräbt 4 s und findet Sculkblumensamen (Beute des Originals).
- **Infektion:** wie im Original nach 600 Ticks, hier aber nur in der Sift (`infection.only-in-sift`,
  Entscheidung Phase 2). Die dunkle Färbung des infizierten Schnüfflers (Client) zeigen hier Seelen-Partikel.
  Ein infizierter Schnüffler gräbt nichts aus (leere Beute im Original).
- **Wächter** (Mixin des Originals): Sie greifen ihn nie an und werden nicht wütend auf ihn; zusätzlich
  prallen alle Wächter-Angriffe an ihm ab (im Original sind sie gar nicht erst möglich). Das Streicheln ist
  nachgebaut (Zeiten, Reichweite 18, Abstand 3,5, 5 s, Herzen); die eigene Streichel-Animation des Wächters
  (Client) fehlt, der Wächter bleibt stehen und sieht ihn an.
- **Schnüffler graben auf Sift-Sculk** wie im Original: Das Datapack ergänzt `#minecraft:sniffer_diggable_block`
  um Sculk und die Sift-Sculk-Blöcke. Weil diese hier Pilzblock-Zustände sind, stehen im Tag die ganzen
  Pilzblöcke (Pilzstiel, brauner und roter Pilzblock). Ein Schnüffler gräbt deshalb auch auf natürlichen
  Pilzblöcken (in der Hauptwelt selten, Riesenpilze), dort ohne Infektion.
- **Graben verändert keine Blöcke** (wie im Original); die Gamerule `mob_griefing` betrifft ihn deshalb nicht.

