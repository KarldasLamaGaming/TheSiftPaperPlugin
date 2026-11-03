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

### Hinzugefügt (3)

Im Original stehen Weidenbretter und -stämme in den Vanilla-Tags `#planks` und `#logs_that_burn` und
funktionieren damit in allen Holzrezepten. Hier sind es Pilzblock-Items, die kein Vanilla-Tag erreicht.
Deshalb gibt es eigene Rezepte für das Nötigste:

| Rezept | Datei |
|---|---|
| 2 Weidenbretter → 4 Stöcke | `plugin/src/main/resources/extra-recipes/overgrown_willow_sticks.json` |
| 4 Weidenbretter → Werkbank | `extra-recipes/crafting_table_from_overgrown_willow_planks.json` |
| Weidenstamm/-holz (auch entrindet) → Holzkohle (Ofen) | `extra-recipes/charcoal_from_overgrown_willow_logs.json` |

Andere Holzrezepte (Truhe, Fass, Holzwerkzeuge, Schilder …) nehmen Weidenbretter nicht an.

### Ergebnis ist ein Vanilla-Item

Rezepte, deren Ergebnis hier nur als Vanilla-Block existiert, ergeben diesen: Siftschiefer-Treppen,
-Stufen und -Mauern → Tiefenschiefer-Varianten; Weidentür, -falltür, -zaun, -zauntor, -knopf,
-druckplatte, -schild, -hängeschild, -regal, -treppe, -stufe und -boot → Mangrove; Ichorglasscheibe →
lila Glasscheibe; Ichorschnee → Schnee.

## Sift-Items und ihre Vanilla-Basis

Sift-Items bauen auf Vanilla-Items auf (Papier, Netherit, Schneeball; Block-Items auf Faden, Glas oder
Pilzblöcken). Alle Sift-Items, seit alpha.6 auch die Block-Items (siehe `docs/ADMIN.md`, Schutz), verhalten
sich nicht wie ihre Basis. Im Original gibt es diese Frage nicht,
weil die Items eigene Item-Typen sind.

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
  vorher). Deshalb darf bis Phase 3 nur ein Viertel der Kandidaten bauen
  (`worldgen.abandoned-portals.chance: 0.25`): 7 von 49 statt 24 von 49, das Original hat 6. Andere Plätze
  als im Original, aber dieselbe Menge. Mit Phase 3 wird der Wert 1.0.
- Das Haupt-Portal wird beim ersten Start gebaut, nachdem die Chunks um 0/0 generiert sind (das Original
  baut es beim Serverstart vor der Generierung). Bäume und Pflanzen auf dem Bauplatz werden dabei entfernt;
  verlassene Portale halten sich vom Suchgebiet fern.
- Die Dunklen Schnüffler in Sculk-Regionen kommen mit den Mobs (Phase 2).
- Monolithen, Bögen, Schnüffler-Höhlen, Ichor-Seen und -Quellen, Lavaböden und Ichorschnee kommen mit
  Phase 3.
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
- Die Melodie 1-3-7-6-5-2-4-8 öffnet das Portal; danach Farbstoß, Nachspielen und eine Fläche, die sich vom
  Rand zur Mitte füllt.
- Durchgang zum Haupt-Portal der Sift und zurück zum Rückkehrpunkt; Transit-Schutz nach jeder Reise.

Unterschiede:
- **Wecken:** Im Original schaltet nur der Singer (Ziegenhorn in der Antiken Stadt) die Konsolen vom
  Horn- in den Noten-Modus. Hier gehen der Singer (seit Phase 2) und zusätzlich der Schlüssel der Dimension
  (Schritt 7b, abschaltbar mit `portals.keys.enabled`) bzw. `/sift portal unlock`.
- **Nur registrierte Portale:** Im Original zündet die Melodie jeden passenden Rahmen im Umkreis von
  10 Blöcken. Hier nur Portale aus Strukturen oder per `/sift portal create`. Die Konsolen eines Portals
  stehen in seinem Eintrag; eine Konsolengruppe über 16 Blöcke wie im Original wird nicht gesucht.
- **Schließen:** Die rückwärts gespielte Melodie schließt das Portal nicht (dauerhaft offen, Entscheidung
  vom 30.10.).
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
  - Der Sift-Kompass zeigt den Weg. Die Schlüssel ersetzen den Singer.
  - Schreine, Schlüssel und Kompass gibt es im Original nicht; ihre Texturen sind eigene.

## Mobs (seit Phase 2)

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
  - Er kommt nur zu **schlafenden** registrierten Portalen (Schrein, Antike Stadt, eigene per Befehl).
  - Die Schallwellen sind Staubpartikel statt des eigenen Partikels; die Lichtsäule am Kopf (Client-Licht)
    fehlt.
  - Wird der Chunk während des Auftritts entladen oder der Server neu gestartet, gilt das Portal als geweckt,
    sobald der Gesang begonnen hatte; der Singer ist danach weg.
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
- **Melodie:** Im Original öffnet dieselbe Melodie wie überall. Hier hat die Antike Stadt eine eigene, längere
  (`portals.ancient-city.melody`, 12 Töne), die nicht im Hinweis steht, sondern in einem Buch in den Truhen der
  Stadt (Entscheidung Phase 2). Das Buch ist neu.
- **Wecken:** zusätzlich zum Singer auch mit dem Echo-Resonator der Hauptwelt.

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

