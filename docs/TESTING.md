# The Sift – Test-Checkliste

Für den separaten Testserver, nie auf dem Produktivserver. Paper 26.3, frische Welt, Vanilla-Client 26.3.
Jeder Punkt: Erwartung in Klammern. Abweichungen bitte mit Screenshot (F3 sichtbar) und Log-Auszug melden.

## Sift-Welt zurücksetzen

Für Tests der Weltgenerierung (z. B. nach einem Update mit neuer Verteilung) nur die Sift neu erzeugen;
Hauptwelt, Nether, End und Spielerdaten bleiben.

1. Alle Spieler aus der Sift holen (`/sift tp` in der Sift bringt zurück in die Hauptwelt), sonst landen
   sie nach dem Neustart in einer neuen Welt an derselben Stelle.
2. Server stoppen.
3. Ordner `<welt>/dimensions/the_sift/the_sift/` löschen (`<welt>` = `level-name` aus
   `server.properties`, meist `world`). Darin liegen Gelände, Haupt-Portal-Ankunftspunkt und das
   Portal-Protokoll; sonst nichts löschen.
4. Bei Bedarf `plugins/TheSift/config.yml` anpassen (oder löschen, dann schreibt das Plugin die neue
   Standard-Config; eigene Werte vorher sichern).
5. Server starten. Erwartung: Log `Selecting a location for the main Sift portal …` und
   `Main Sift portal ready at x y z`; `/sift debug portals` ist leer.
6. Für viele Portale auf einmal Chunks vorgenerieren (z. B. Chunky in `the_sift:the_sift`), danach
   `/sift debug portals`.

## Vorbereitung

- [ ] Plugin-Jar und `resource-pack.url` aus demselben Release (Standard passt automatisch).
- [ ] Log beim Start: `Datapack installed`, `Found new data pack TheSift/the_sift`,
      `The Sift is loaded as world …`, `Resource pack … (SHA-1 …)`; keine `ERROR`-Zeilen von TheSift.
- [ ] `/datapack list` (TheSift/the_sift aktiv), `/sift pack info` (URL und SHA-1 wie
      `the-sift-pack.zip.sha1` im Release).
- [ ] Erster Start mit neuer Welt: Log `Main Sift portal ready at x y z` (nach `Selecting a location ...`).
      Zweiter Start: diese Zeilen erscheinen nicht noch einmal.

## Resource Pack

- [ ] Beim Betreten erscheint die Pack-Abfrage mit dem deutschen Text (`resource-pack.prompt`).
- [ ] Annehmen: Pack lädt ohne Fehlermeldung; Itemnamen der Sift-Items erscheinen deutsch.
- [ ] Ablehnen bei `required: false` (Standard): Spieler bleibt verbunden; Sift-Blöcke sehen wie
      Pilzblöcke/Faden aus, Namen erscheinen trotzdem deutsch.
- [ ] Mit `required: true` und `/sift pack reload`: Ablehnen trennt mit `kick-on-decline`.
- [ ] `/sift pack send <spieler>` schickt das Pack erneut.

### Sprachen Deutsch/Englisch (ab alpha.42c.1: Pack)

Smoke-Test ohne Spieler: Selbsttest „Sprachdateien des Packs: 291 Keys Deutsch, 291 Englisch, alle in beiden Sprachen
mit gleichen Platzhaltern“; Pack enthält `assets/lamakingdom/lang/en_us.json`; Advancements des Originals haben jetzt
englische Ersatztexte (z. B. „Tame the Blub“).

**Nur mit Spieler** (zwei Durchgänge: Client auf Englisch, dann auf Deutsch; Optionen → Sprache):
```
/sift give @s golem_trophy
/sift give @s echo_resonator
/sift give @s sift_compass
/sift give @s charoite_block
/sift give @s temple_rumor
/sift items
```
- [ ] **Englisch:** Night Crystal, Echo Resonator, Sift Compass, Block of Charoite, Temple Rumor; Sift-Items des
      Originals englisch (z. B. Siftite Ingot). Advancement-Tab: „Silence in the Temple“ (vorher fälschlich deutsch).
- [ ] **Deutsch:** dieselben Items als Nachtkristall, Echo-Resonator, Sift-Kompass, Charoit-Block, Gerücht vom Tempel;
      Advancement „Stille im Tempel“.
- [ ] **Ohne Pack** (ablehnen): Namen erscheinen englisch (Ersatztext).
- [ ] **Andere Sprache** (z. B. Français): englische Namen.

### Sprachen Deutsch/Englisch (ab alpha.42c.2: Meldungen des Plugins)

Smoke-Test ohne Spieler: alte config.yml mit deutschen Einzeltexten → Log „old defaults replaced … (alpha42c):
resource-pack.prompt, kick-on-decline, failed-message, altar-message, hints.text, hints.all-defeated“, danach `de:`/`en:`;
`messages_en.yml` angelegt; Selbsttest „Spielertexte: 104 Keys Deutsch, 104 Englisch …“; ein Blub trägt den Namen
`{translate: "entity.the_sift.blub", fallback: "Blub"}`; Bosskampf mit zwei Bossleisten ohne Fehler; Konsole bleibt
deutsch (`language: de`).

**Nur mit Spieler** (jeden Punkt zweimal: Client Englisch und Deutsch; Optionen → Sprache, dann neu verbinden):
```
/sift give @s sift_compass
/sift give @s echo_resonator
/sift give @s charoite_core
/sift give @s soul_block 16
/sift temple spawners clear temple-1
/sift portal schedule lock overworld
/sift portal schedule auto overworld
```
- [ ] **Pack-Abfrage:** beim Verbinden der Text der eigenen Sprache; mit `required: true` und Ablehnen der Kick-Text
      der eigenen Sprache.
- [ ] **Kompass:** „Temple: N blocks“ / „Tempel: N Blöcke“, „Portal: N blocks“ / „Portal: N Blöcke“.
- [ ] **Konsole/Portal:** falscher Schlüssel, Konsole schläft, versiegeltes Portal mit Datum und „3 d 4 h“ / „3 T 4 Std.“.
- [ ] **Zeitsperre:** `/sift close` → Countdown und „The Sift closes in …“ / „Die Sift schließt in …“; `/sift open`
      → Ankündigung je Sprache (zwei Spieler mit verschiedenen Sprachen gleichzeitig).
- [ ] **Enchanter:** Ritual ohne Level („The ritual needs 30 …“), Menütitel, Kosten-Zeile und „Click to raise“.
- [ ] **Tempel:** Abbauen (Schutz-Text), Altar mit wachen Spawnern („… brood cores are awake“), Altar ohne Seelenblöcke.
- [ ] **Boss:** Countdown, Titel „Great Echo Golem · 600 health · 1 players“, Bossleiste „Armour, 12 plates“, Phasen-Texte,
      Sieg „You receive: a Night Crystal, Netherite … (enchanted)“; zu zweit mit verschiedenen Sprachen: jeder sieht die
      Bossleiste in seiner Sprache.
- [ ] **Gerüchte-Zettel:** Truhe mit englischem Client öffnen → englischer Zettel („… blocks to the northeast“), mit
      deutschem Client deutscher.
- [ ] **Todesmeldung:** von einem Sifter getötet → „… was slain by Sifter“ bzw. „… wurde von Sifter erschlagen“.
- [ ] **Sonstiges:** Ichor in Wasser schütten („Ichor and water do not mix.“), Sift-Block in der Oberwelt setzen.

## Blöcke

**Platzieren ohne Aufblitzen (ab alpha.42g):**
- **Smoke-Test:** `sift debug selftest` meldet „Block-Zustand im Item (Vorhersage beim Platzieren): 40 von 40
  Sift-Blöcken; … mit anderem Item gesetzt: ichor_glass (glass)“ – bestanden.
- **Nur mit Spieler** (frisch geholte Items, `/sift give`):
  - [ ] Je ein Block aus jeder Familie mehrmals abbauen und sofort wieder setzen, auch an Kanten und neben gleichen
        Blöcken: Siftschiefer, ein Erz, Seelenblock, Siftit-Block, Charoit-Block, Sculk Enchanter, eine Pflanze (Lotus)
        und Weidenbretter. Es blitzt kein Vanilla-Pilzblock und kein Faden mehr auf.
  - [ ] Dasselbe mit etwas Ping (z. B. vom anderen Ende der Welt oder mit Clumsy).
  - [ ] Weidenstamm liegend setzen: höchstens ein kurzer Wechsel der Achse, keine Pilz-Optik.
  - [ ] Ichorglas: kurz klares Glas, dann Ichorglas (bekannt, siehe ABWEICHUNGEN).
  - [ ] Ein Block aus einer Truhe verlassener Portale verhält sich genauso.

**Abbauzeit mit Äxten (Fix alpha.35).** Vorher bauten Eisen-, Diamant-, Netherit- und Goldäxte jeden Sift-Block auf
einem Pilzblock-Ersatz sofort ab (Siftschiefer, Erze, Sculk, Weidenholz …). In der Sift, Überlebensmodus:
```
/give @s iron_axe
/give @s diamond_axe
/give @s diamond_pickaxe
```
- [ ] Siftschiefer mit Diamantaxt: etwa 15 s wie Tiefenschiefer mit der Axt (falsches Werkzeug), kein Sofortabbau;
      mit Diamantspitzhacke etwa 0,6 s; von Hand etwa 15 s.
- [ ] Weidenstamm mit Diamantaxt: kurz (wie Eichenstamm), nicht sofort; Weidenlaub wie in #11.
- [ ] Gegenprobe ohne Client: `/sift debug break <x> <y> <z> diamond_axe` → dieselben Ticks wie gefühlt.
- [ ] Den Block nach dem Abbau nicht wieder auftauchen sehen (kein „Geisterblock“) und Zielwechsel zwischen Sift- und
      Vanilla-Blöcken: Vanilla-Blöcke bauen sich normal ab.

- [ ] `/sift tp`: Gelände in Rot-/Lilatönen wie im Original, orange Flecken = trockener gesunder Sculk.
- [ ] `/sift items` → Blöcke/Erze/Pflanzen: jeder Block hat die Original-Textur (keine lila-schwarzen
      Würfel, keine Pilzblock-Optik).
- [ ] Platzieren in der Sift: Block erscheint mit Original-Optik; Pflanzen nur auf Sift-Boden
      (sonst „Kann hier nicht wachsen.“), hängende Wurzeln nur unter festen Blöcken.
- [ ] Platzieren in der Overworld: abgelehnt mit „Sift-Blöcke lassen sich nur in der Sift platzieren.“

## Weide, Lotus, Ichorglas, Ichor (ab alpha.3)

- [ ] Weidenstamm, -holz und die entrindeten Varianten an Boden, Decke und alle vier Seiten setzen:
      Stamm liegt in Richtung der angeklickten Fläche (stehend auf Boden/Decke, liegend an Seiten),
      Ringe an den Stirnseiten wie im Original.
- [ ] Rechtsklick mit Axt auf Weidenstamm/-holz: wird entrindet, Achse bleibt, Entrinden-Geräusch,
      Axt verliert 1 Haltbarkeit (im Kreativmodus nicht). Rechtsklick ohne Axt: nichts passiert.
- [ ] Abbau: Weidenstamm mit Diamantaxt ≈ 0,4 s, von Hand ≈ 3 s, droppt sich selbst.
      Weidenlaub mit Eisenhacke ≈ 0,2 s, droppt sich selbst. Gegenprobe mit `/sift debug break`.
- [ ] Weidenbretter und -laub sehen wie im Original aus (Laub ist deckend, keine Löcher).
- [ ] Lotus: nur auf Sift-Boden setzbar, Original-Optik, ohne Kollision, droppt sich selbst.
- [ ] Ichorglas in Grafik **„Schnell“** und **„Schön“/„Fabelhaft“**: durchscheinend lila, Blöcke
      dahinter sichtbar; zwei Glasblöcke nebeneinander zeigen die Innenflächen (anders als im Original
      nicht ausgeblendet, bekannte Einschränkung).
- [ ] Ichorglas: man kann darauf stehen und läuft nicht mehr halb hinein; seitlich bleibt ein kleiner
      Spalt an den Ecken (Kreuzform). `/sift debug shape <x> <y> <z>` zeigt 4 Boxen, ≈ 61 %.
- [ ] Ichorglas aus alpha.2 (vorher gesetzt): sieht weiter wie Ichorglas aus, `shape` zeigt ≈ 24 %;
      abbauen und neu setzen ergibt die neue Form.
- [ ] Wasser in der Sift (Seen, Eimer, Kessel) ist ichorfarben hellblau-lila, in der Overworld normal.
- [ ] Gegenprobe Chorus im End: `/sift debug chorus 6 0 100` (und weitere Stellen) meldet
      „0 in Sift-Zuständen“.

## Ichor (ab alpha.21)

- [ ] Im Ichor schwimmen: Regeneration-Symbol ohne Partikel, Leben füllt sich; nach dem Verlassen noch ~5 s.
- [ ] Spieler werden im Ichor nicht zusätzlich gebremst; ein Tier oder ein Item im Ichor treibt langsamer
      als in Oberwelt-Wasser.
- [ ] Zwei Ichor-Quellen mit einem Loch dazwischen: Das Loch wird keine Quelle (keine unendlichen Quellen).
- [ ] Lava fließt seitlich an Ichor: Siftschiefer-Bruchstein; Lava fließt auf Ichor: Siftschiefer; Ichor
      erreicht eine Lavaquelle: Obsidian.
- [ ] Eimer an Ichor: Ichoreimer (eigenes Bild, Name „Ichoreimer“), Advancement „Blut der Götter“. Leeren
      setzt Ichor bzw. in der Oberwelt Wasser. Eimer an vollem Kessel in der Sift: Ichoreimer.
- [ ] Glasflasche an Ichor-Quelle: Ichorflasche; an fließendem Ichor: nichts. Glasflasche am Kessel: Ichorflasche,
      Kessel −1. Ichorflasche auf leeren oder halben Kessel: +1 Stufe, Glasflasche zurück. Klänge hörbar.
- [ ] Mit einem Stapel Glasflaschen: eine wird verbraucht, die Ichorflasche landet im Inventar.
- [ ] Nebel: An einem Ichor-See sieht man gelegentlich türkis/rosa/goldene Staubpartikel über der Oberfläche.
- [ ] Sniffer-Ei an Ichor ausbrüten: Das Junge erscheint nicht, wenn es in oder über Ichor schlüpft.
- [ ] `ichor.enabled: false` + Neustart: nichts davon passiert (Gamerule bleibt, wie sie zuletzt war).

**Ichor bleibt Ichor, Wasser bleibt Wasser (ab alpha.38)**
```
/give @s water_bucket
/give @s bucket 2
/give @s glass_bottle 4
/give @s cauldron
/give @s dispenser
/sift give @s ichor_bucket
```
- [ ] Oberwelt: Ichoreimer ausgießen. Darin schwimmen: Regeneration (wie in der Sift); ein Tier treibt langsamer.
      `/sift debug ichor marks` an der Stelle: „Ichor (Ausnahme, markiert)“, auch im fließenden Teil.
- [ ] Oberwelt: Eimer an diesem Ichor ergibt einen Ichoreimer, an normalem Wasser daneben einen Wassereimer.
      Glasflasche am ausgegossenen Ichor: Ichorflasche.
- [ ] Sift: Wassereimer ausgießen. Darin keine Regeneration; Eimer daran ergibt einen Wassereimer, Glasflasche
      eine Wasserflasche. Daneben normales Ichor wirkt wie bisher.
- [ ] Kessel: In der Oberwelt einen Ichoreimer hinein; mit Eimer wieder entnehmen ergibt einen Ichoreimer, eine
      Glasflasche eine Ichorflasche. Eine Wasserflasche in den Ichor-Kessel: „Ichor und Wasser mischen sich nicht“.
- [ ] Werfer mit Ichoreimer in der Oberwelt (Redstone): setzt Ichor. Werfer mit leerem Eimer davor: bekommt einen
      Ichoreimer.
- [ ] Schwamm an ausgegossenem Ichor: saugt es auf; danach dort Wasser aus einem Wassereimer ist wieder Wasser.
- [ ] Neustart: ausgegossenes Ichor ist danach immer noch Ichor.
- [ ] `ichor.identity.enabled: false` + Neustart: wie bis alpha.37 (Ichoreimer setzt in der Oberwelt Wasser).

## Abbau

- [ ] Siftschiefer: Diamantspitzhacke ≈ 0,6 s, von Hand ≈ 15 s ohne Drop, Axt langsam wie von Hand.
- [ ] Siftiterz: mit Eisenspitzhacke langsam und ohne Drop, mit Diamantspitzhacke ≈ 0,85 s.
- [ ] Verstärkter Siftschiefer: nicht abbaubar (Risse erscheinen nicht).
- [ ] Gegenprobe: `/sift debug break <x> <y> <z> diamond_pickaxe` zeigt dieselben Zeiten.

## Drops

- [ ] Siftschiefer → Siftschiefer-Bruchstein; mit Behutsamkeit → Siftschiefer.
- [ ] Ichorglas droppt nur mit Behutsamkeit.
- [ ] Erze ohne Behutsamkeit: Kohle/Diamant/Smaragd/Charoit (4–9)/Siftitklumpen, mit XP;
      mit Glück III mehr; mit Behutsamkeit das Erz selbst ohne XP.
- [ ] Sift-Geröll: meist Geröll, etwa jedes 10. Mal ein Ichorsplitter; mit Glück III immer
      Ichorsplitter; mit Behutsamkeit immer Geröll. Statistik:
      `/sift debug break <x> <y> <z> iron_pickaxe none 1000` (≈ 100 Splitter).
- [ ] Pflanzen: Halme, Sprossen, Mangold, Wedel, hängende Wurzeln nur mit Schere oder Behutsamkeit;
      Sonnenstrahlpflanze, Flüsterblüte, Sculkblume, Setzling immer.
- [ ] Untergrund abbauen → Pflanze darauf fällt ab und droppt (wie oben).
- [ ] TNT neben Sift-Blöcken: Sift-Items droppen, verstärkter Siftschiefer bleibt stehen.
- [ ] Erze finden: `/sift debug find siftslate_siftite_ore` (Siftiterz nur y 0–15).

## Update-Schutz (Sift)

- [ ] Block (Stein, Erde, Holz) direkt neben/auf/unter Sift-Blöcken setzen und abbauen: Die
      Sift-Blöcke behalten ihr Aussehen (kein Wechsel zu einem anderen Sift-Block).
- [ ] Fackel/Blume auf einem Vanilla-Block neben Sift-Blöcken, dann den Vanilla-Block abbauen:
      Fackel/Blume fällt ab.
- [ ] Wasser/Lava neben Sift-Blöcken ausgießen und Nachbarblock abbauen: fließt nach.
- [ ] Kolben schiebt Sift-Block: Aussehen bleibt; schiebt gegen Pflanze: Pflanze droppt Sift-Item.
- [ ] `/execute in the_sift:the_sift run setblock … strict` erhält Sift-Zustände (ohne `strict` nicht).

## Item-Menü und Rechte

- [ ] `/sift items` öffnet „Sift-Items: Blöcke (1/1)“; Reiter wechseln Kategorien, aktiver Reiter glänzt.
- [ ] Klick = 1 Item, Shift-Klick = ganzer Stapel (64, Ichorschneeball 16); volles Inventar → Item fällt
      vor den Spieler.
- [ ] Items ins Menü legen, ziehen oder per Zahlentaste tauschen geht nicht.
- [ ] Werkzeuge/Rüstung: Siftit-Werkzeuge, Speer und Rüstung mit Original-Optik.
- [ ] Reiter-Symbole unten (z. B. Werkzeuge = Siftitschwert, Rüstung): Tooltip nur mit Namen, ohne
      Angriffsschaden, Rüstung oder andere Attribute. Die Items im Menü selbst zeigen ihre Werte weiter.
- [ ] `/sift give <spieler> <id> [anzahl]`: Tab-Vervollständigung zeigt alle Ids.
- [ ] Spieler nur mit `thesift.admin.items`: `/sift items` und `/sift give` gehen, `/sift tp`,
      `/sift pack`, `/sift debug` nicht sichtbar. Spieler ohne Rechte: `/sift` unbekannt.

## Items, Rezepte, Siftit (ab alpha.4)

- [ ] `/sift debug selftest`: alle Zeilen „OK“, am Ende „Selbsttest bestanden“.
- [ ] Rezeptbuch: Sift-Rezepte sind nach dem Betreten freigeschaltet (Siftitbarren, Weidenbretter,
      Ichorglas, Siftschiefer-Steinsäge …).
- [ ] Weidenbretter in Holzrezepten (ab alpha.24, #14): Truhe, Fass, ein Bett, Holzspitzhacke, Schild und
      Bücherregal aus Weidenbrettern; Lagerfeuer und Räucherofen aus Weidenstämmen. Die Rezepte stehen im
      Rezeptbuch. Mit Eichenbrettern entsteht weiterhin das Vanilla-Rezept; gemischte Bretter ergeben nichts.
- [ ] Werkbank: 9 Siftitklumpen → Siftitbarren; Siftitbarren → 9 Klumpen; Weidenstamm → 4 Weidenbretter
      (auch umbenannt); Weidenbretter → Stöcke, Werkbank; Ichorscherben → Ichorglas; Siftschiefer-Bruchstein
      statt Bruchstein für Spender, Werfer, Kolben, Beobachter, Hebel.
- [ ] Steinsäge: Siftschiefer → Ziegel, Bruchstein, gemeißelt; Treppen/Stufen/Mauern ergeben die
      Tiefenschiefer-Varianten.
- [ ] Ofen/Räucherofen/Lagerfeuer: rohes → gebratenes Sifterfleisch; Weidenstamm → Holzkohle.
- [ ] Schmiedetisch: Aufwertungsvorlage + Netheritschwert + Siftitbarren → Siftitschwert (Verzauberungen
      und Name bleiben). Gleiches für alle Werkzeuge, Speer und Rüstungsteile.
- [ ] Siftitschwert: Tooltip 8,5 Angriffsschaden, Haltbarkeit 2200 (F3+H); Siftitspitzhacke baut schneller
      ab als Netherit; Siftitrüstung: Optik der Sift, Rüstung wie Netherit, Härte 3, Rückstoßresistenz
      12 %; Anlegegeräusch.
- [ ] Siftit ist kein Netherit (kein Ergebnis bzw. kein Handel):
  - [ ] Amboss: Siftitschwert + Netheritbarren; Netheritschwert + Siftitschwert; Netheritschwert +
        Siftitbarren; Siftitschwert + Siftithelm.
  - [ ] Amboss erlaubt: Siftitschwert + Siftitbarren (Reparatur), zwei Siftitschwerter, Verzauberungsbuch,
        Umbenennen.
  - [ ] Schmiedetisch: Netherit-Aufwertung mit Siftit-Vorlage oder Siftitbarren; Siftitschwert als Basis der
        Netherit-Aufwertung. Erlaubt: Rüstungsbesatz auf Siftitrüstung.
  - [ ] Werkbank: zwei beschädigte Siftitschwerter kombinieren; Siftitbarren/-klumpen als Papier (Buch,
        Karte).
  - [ ] Schleifstein: Siftitschwert + Netheritschwert. Erlaubt: Siftitschwert allein (entzaubern).
  - [ ] Kartograf/Bibliothekar: Siftitbarren werden nicht als Papier angenommen.
  - [ ] Sift-Materialien wie Papier: 3 Charoit + Leder ergibt kein Buch; Ichorscherben/Charoit im
        Kartentisch und beim Bibliothekar abgelehnt; 4 Ichorschneebälle ergeben Ichorschneeblock, keinen
        Schneeblock.
  - [ ] Block-Items (ab alpha.6): Überwucherte Wedel/Halme/Sprossen wie Faden (Bogen, Angel, Wolle) ergeben
        nichts; Ichorglas wie Glas (Leuchtfeuer, Glasflaschen) ergibt nichts. Erlaubt: 6 Ichorglas →
        Ichorglasscheibe, Weidenbretter → Stöcke/Werkbank, Weidenstamm → Holzkohle, Siftschiefer in der
        Steinsäge, Weidenholz als Ofen-Brennstoff.
- [ ] Gegenprobe Netherit unverändert: Netherit-Aufwertung, Reparatur mit Netheritbarren, zwei
      Netheritschwerter kombinieren, Netheritblock aus 9 Barren.
- [ ] Essen: rohes/gebratenes Sifterfleisch essbar (3 bzw. 8 Hunger); Ichorflasche trinkbar
      (Regeneration 3 s, Glasflasche bleibt).
- [ ] Schallplatten Rift und Wellspring im Plattenspieler: Musik mit Titelanzeige.
- [ ] Truhen-Loot: Truhe mit `/setblock ~ ~ ~ chest{LootTable:"the_sift:chests/abandoned_portal"}` setzen und
      öffnen: echte Sift-Items (deutsche Namen, stapelbar mit Items aus `/sift items`).
- [ ] Vanilla-Truhen-Loot unverändert (alpha.6-Blocker): `/setblock ~ ~ ~ chest{LootTable:"minecraft:chests/simple_dungeon"}`
      in der Hauptwelt öffnen: normaler Inhalt, nicht leer. `/sift debug selftest` füllt dafür selbst je eine
      Truhe mit einer Vanilla- und einer Sift-Tabelle („Truhe mit … gefüllt“).
- [ ] Advancements: Reiter „Die Sift“ beim Betreten der Sift; Charoiterz/Siftiterz abbauen, Holz in der
      Sift anzünden, volle Siftitrüstung (Herausforderung) werden vergeben.

## Weltgenerierung und Haupt-Portal (ab alpha.5)

- [ ] `/sift tp`: Ankunft am Haupt-Portal (nicht im Boden, nicht in der Luft); Rahmen aus verstärktem
      Siftschiefer, Hügel mit Klippen an beiden Enden, freier Vorplatz vor den Öffnungen, Pflanzen auf den
      Simsen. Kein Baum im Portal.
- [ ] Überwucherter Wald (`/execute in the_sift:the_sift run locate biome the_sift:overgrown_forest`): viele
      Weiden, klein und groß, verschieden gedreht, Stämme mit Ringen in der richtigen Richtung, Wurzeln am
      Boden, hängende Ranken (kletterbar); Laub wie im Original.
- [ ] Weide abbauen: Stamm/Holz droppen sich, Ranken fallen nicht von selbst ab, solange sie hängen.
- [ ] Blumen- und Pflanzenflecken (Lotus, Sonnenstrahlpflanze, Flüsterblüte; Halme, Wedel usw. auf
      Siftschiefer) in allen Biomen.
- [ ] Oberflächen-Sculk: `/sift debug landmarks` zeigt die Mitten der Sculk-Regionen; dort große Flecken
      aus Vanilla-Sculk mit Sensoren und Kreischern (Kreischer können einen Wärter rufen!), Adern am Rand.
- [ ] Verlassene Portale: nach dem Generieren (z. B. Chunky) listet `/sift debug portals` die gebauten
      Portale; Klick auf die Koordinaten teleportiert zur Truhe. Dort steht ein Portal aus Siftschiefer und
      Sculk mit sanft angeglichenem Gelände drumherum. Truhe öffnen: Beute aus
      `the_sift:chests/abandoned_portal` (echte Sift-Items, oft die Aufwertungsvorlage oder die Schallplatte
      Rift).
- [ ] Menge: nach dem Vorgenerieren ist etwa jeder siebte Kandidat gebaut (Original: 8 von 52); die anderen
      stehen mit ihrem Grund (Gelände, Platz reserviert …) in `/sift debug portals`.
- [ ] Abgelehnte Kandidaten aus `/sift debug portals` stichprobenartig anschauen: Der Grund passt zum
      Gelände (steiler Hang, Baum oder Ichor in der Chunkmitte …).
- [ ] `/sift debug landmarks <x> <z>` um eine besuchte Gegend: jeder Kandidat hat einen Status (gebaut,
      abgelehnt mit Grund, noch nicht generiert).
- [ ] `/sift debug worldgen` nach dem Herumfliegen: Zähler steigen, `0 Fehler`.
- [ ] Weidenholz brennt: Feuerzeug an einen Weidenstamm, Laub, Bretter; Feuer bleibt an Weidenblöcken und
      frisst sie langsam (Laub schnell), springt auf benachbarte Weidenblöcke über. Gamerule
      `fire_spread_radius_around_player 0` stoppt es.
- [ ] Ofen: Weidenstamm, -holz, -bretter (je 1,5 Items) und -setzling (0,5) per Klick und Shift-Klick in den
      Brennstoff-Slot; der Ofen brennt. Weidenlaub geht nicht hinein.

## Ichor-Seen und -Quellen (ab alpha.25)

Nur in neu generierten Chunks. Zum Testen in ein unerkundetes Gebiet der Sift gehen, z. B.
`/execute in the_sift:the_sift run tp @s 20000 150 20000`, und ein paar hundert Blöcke herumfliegen.
- [ ] `/sift debug ichor`: Es werden Seen und Quellen mit Koordinaten gelistet; [Teleport] klicken.
- [ ] See ansehen:
  - Ufer flach und zum Wasser hin abfallend; Becken aus Sculk oder Siftschiefer.
  - Kleine Pflanzen am Ufer; bei großen Seen Felsen im Wasser.
  - Ichor-Farbe.
- [ ] Nachts leuchtet der See (Lichtniveau mit F3 an der Oberfläche etwa 12). Im Ichor schwimmen gibt
      Regeneration (alpha.21).
- [ ] (bis alpha.35, nur alte Chunks) Eimer an einem leuchtenden Block der Oberfläche (F3 zeigt `light` im
      Ziel): Danach ist dort Luft, kein unsichtbarer Lichtblock mehr. Dasselbe mit einem Schwamm am Ufer.
- [ ] Quelle über `/sift debug ichor` [Teleport] ansteuern: Ichor fließt aus einer Felswand, über der Öffnung
      ist es hell.

**Regression alpha.36 (keine versteckten Quellen)** – nur in nach alpha.36 generierten Chunks
- [ ] `/sift debug ichor scan 8` an mehreren Stellen: „0 wassergeloggte Lichtblöcke“, dazu einige Leuchtlichter.
- [ ] See: Mit F3 auf die Oberfläche schauen: Der Zielblock ist Wasser (`minecraft:water`, `level=0`), kein
      `light`. Über jedem dritten Block hängt ein unsichtbarer Lichtblock (mit einem Lichtblock-Item in der Hand
      sichtbar), F3 zeigt dort Blocklicht 12.
- [ ] Quelle: den Quellblock in der Felswand mit dem Eimer nehmen. Das fließende Ichor darunter läuft innerhalb
      weniger Sekunden vollständig ab; es entsteht keine neue Quelle. Den Eimer wieder ausgießen: Es fließt wieder.
- [ ] Eimer Ichor in die Luft über dem See gießen (genau dort, wo ein Lichtblock hängt): Es entsteht eine
      sichtbare Ichor-Quelle; mit dem Eimer wieder aufnehmen, danach ist das Licht dort weg.
- [ ] `/sift debug worldgen`: Zeile „Ichor-Seen … Zeit je Kandidat“ (im Mittel wenige Millisekunden).
- [ ] Ab alpha.25.1: `/sift debug worldgen` zeigt nicht gebaute Seen je Variante (z. B. „SMALL zu uneben“,
      „CASCADE Kaskade: unten zu uneben“) und „Weideninseln“.
- [ ] Kaskade (selten, nur in Hügeln): unterer See, darüber ein Teich auf einem flachen Sims, dazwischen ein
      Wasserfall aus Ichor. Weideninsel (selten, große überwucherte Seen): Insel mit einer Weide im See.
- [ ] `worldgen.ichor-lakes.enabled: false` bzw. `ichor-springs.enabled: false` und Neustart: In neuen Chunks
      entstehen keine Seen bzw. Quellen.

## Ichor-Schnee und False Alarm (ab alpha.26)

- [ ] In ein neues Ichor-Schneegipfel-Gebiet gehen:
      `/execute in the_sift:the_sift positioned 90000 150 90000 run locate biome the_sift:ichor_snowy_peaks`,
      dann den Teleport-Klick bzw. `/execute in the_sift:the_sift run tp @s <x> 200 <z>`.
- [ ] Der Boden ist überall verschneit (meist 1 Schicht, stellenweise 2), Weidenkronen tragen Schnee,
      Siftschiefer-Bewuchs unter dem Schnee sieht verschneit aus, keine kleinen Pflanzen unter dem Schnee.
- [ ] Außerhalb der Ichor-Schneegipfel liegt kein generierter Schnee.
- [ ] `/sift debug worldgen` → Zeile „Ichor-Schnee: … Schichten in … Chunks“.
- [ ] False Alarm: `/sift give @s ichor_snowball 4`, einen Wärter herbeirufen (z. B. `/summon warden ~5 ~ ~`
      in einer Testwelt) und einen Ichor-Schneeball in seine Nähe werfen (bis 16 Blöcke) → Advancement
      „False Alarm“. Ein normaler Schneeball gibt es nicht.
- [ ] `worldgen.ichor-snow.enabled: false` bzw. `ichor.false-alarm: false` + Neustart: kein Schnee bzw. kein
      Advancement.

## Monolithen und Bögen (ab alpha.27)

Nur in **neu generierten** Chunks. Nur mit Spieler (Optik):
- [ ] Neues Gelände erzeugen: weit hinaus teleportieren, z. B.
      `/execute in the_sift:the_sift run tp @s 20000 200 20000`, ein paar Minuten herumfliegen (oder Chunky).
- [ ] `/sift debug landmarks` → „Monolithen bekannt: … üppig, … kahl; Bögen: …“ mit den je drei nächsten.
      Klick auf **[Teleport]** stellt dich auf die Spitze des Monolithen bzw. auf den Scheitel des Bogens.
- [ ] **Üppiger Monolith:** schiefe Siftschiefer-Säule (Radius 5–7, 31–55 hoch), nach oben schmaler, drei
      Terrassen-Wülste, unregelmäßiger Rand, am Fuß ein Wurzelwulst ins Gelände. Oben Siftschiefer-Bewuchs mit
      Pflanzen. 1–3 kleinere Nebensäulen daneben, meist ebenfalls bewachsen.
- [ ] **Kahler Monolith:** wie oben, aber schlanker (Radius 4–6, 24–44 hoch), oben nackter Siftschiefer, höchstens
      eine Nebensäule.
- [ ] **Bogen:** natürliche Brücke über eine Senke, 26–30 Blöcke zwischen den Füßen, aus Gesundem Sculk (mit
      Streifen aus trockenem Sculk an der Unterseite) oder Siftschiefer; die Füße gehen weich ins Gelände über,
      ein paar Einkerbungen, oben Pflanzen; auf Bewuchs-Boden ein Siftschiefer-Bogen mit bewachsener Oberseite.
      Bögen sind selten, weil nur wenige Chunks eine passende Senke haben.
- [ ] Kein Monolith und kein Bogen in 22 bzw. 23 Blöcken um das Haupt-Portal, keine Monolithen dicht
      nebeneinander oder in Seen.
- [ ] `/sift debug worldgen` → Zeile „Monolithen: … Bögen: …“ mit Gründen und Zeit je Kandidat.
- [ ] Schalter: `worldgen.monoliths.enabled: false` (oder `lush.enabled` / `bare.enabled`) bzw.
      `worldgen.arches.enabled: false`, Neustart, neues Gelände: keine neuen Monolithen bzw. Bögen.

## Sculk-Spitzen, Bewuchs-Aufräumen, Lavaboden (ab alpha.30)

Nur in **neu generierten** Chunks. Nur mit Spieler:
- [ ] Neues Gelände erzeugen (`/execute in the_sift:the_sift run tp @s 40000 200 40000`, ein paar Minuten fliegen),
      dann `/sift debug landmarks` → „trockene Sculk-Spitzen: …“ mit den drei nächsten; **[Teleport]** stellt dich
      auf die Spitze.
- [ ] Die Spitze: schiefe, nach oben spitz zulaufende Nadel aus trockenem Sculk (19–31 hoch), mit Wurzelwulst und
      2–4 kleineren Spitzen daneben.
- [ ] Lavaboden: in neuen Chunks tief hinab graben oder in eine tiefe Höhle fliegen
      (`/execute in the_sift:the_sift run tp @s ~ 15 ~` im Zuschauermodus): Hohlräume bis y 11 sind mit Lava gefüllt.
- [ ] Bewuchs-Aufräumen: Unter Bäumen, Felsen und Monolithen liegt kein grüner Siftschiefer-Bewuchs mehr, nur
      kahler Siftschiefer bzw. trockener Sculk; über Ichor-Seen schweben keine Pflanzen.
- [ ] `/sift debug worldgen` → Zeilen „Trockene Sculk-Spitzen: …“ und „Oberste Schicht: …“.
- [ ] Schalter: `worldgen.dry-sculk-spikes.enabled`, `worldgen.covered-growth-cleanup.enabled`,
      `worldgen.lava-floor.enabled` je auf `false`, Neustart, neues Gelände: keine Spitzen / kein Aufräumen /
      keine Lava (Hohlräume unter y 11 bleiben leer).

## Wetter und Ichor-Schneefall (ab alpha.32)

Nur mit Spieler (Niederschlag bildet nur in Chunks bei Spielern Schnee):
- [ ] Eigenes Wetter: in der Hauptwelt `/weather clear`, dann `/sift tp` und `/weather rain` in der Sift.
      `/sift debug weather` → Hauptwelt klar, Sift Regen; zurück in der Hauptwelt regnet es nicht.
- [ ] In die Ichor-Schneegipfel: `/execute in the_sift:the_sift positioned 0 100 0 run locate biome
      the_sift:ichor_snowy_peaks`, Teleport-Klick. Bei `/weather rain` fällt dort sichtbar Schnee, auch weit unten,
      und auf dem Boden bildet sich nach und nach eine Schneeschicht (Vanilla: zuerst 1 Schicht).
- [ ] Neben einer Fackel (Blocklicht ab 10) bildet sich kein Schnee.
- [ ] In anderen Biomen (z. B. `the_sift:siftslate_peaks`, `the_sift:overgrown_forest`) regnet es nur, es
      entsteht kein Schnee, auch nicht auf hohen Gipfeln.
- [ ] Ein Ichor-See oder ein mit `/setblock ~ ~ ~ water` gesetzter Block in den Schneegipfeln friert nicht zu.
- [ ] `/sift debug weather` → „Schnee gebildet …, abgelehnt …“ mit den Biomen der Ablehnungen.
- [ ] Mehr Schichten: `/gamerule minecraft:snow_accumulation_height 8` in der Sift → es bleiben höchstens 2.
- [ ] Schalter: `weather.ichor-snowfall.enabled: false` und Neustart → Vanilla-Verhalten (Schnee nur hoch oben
      in den Schneegipfeln, Regen unten); `weather.ichor-freezes: true` → Ichor friert in den Schneegipfeln.

## Himmel und Atmosphäre (ab alpha.29)

Nur mit Spieler:
- [ ] In die Sift gehen (`/sift tp`): Am Himmel sind keine Wolken, auch nicht nach `/weather rain` (Original:
      Wolken nur bei Regen; Vanilla kann das nicht unterscheiden, deshalb ganz aus).
- [ ] `/sift debug intro reset`, dann die Sift verlassen und wieder betreten: Kurz nach der Ankunft (Boden unter
      den Füßen) spielt einmal der Intro-Klang. Ein zweites Betreten spielt ihn nicht mehr.
- [ ] Wiedergabe-Test ohne Reisen: `/sift debug intro play`; Status mit `/sift debug intro`.
- [ ] Nach einem Server-Neustart spielt der Klang für denselben Spieler nicht erneut.
- [ ] Schalter: `atmosphere.clouds: true` und Neustart → Wolken wieder sichtbar; `atmosphere.intro-sound.enabled:
      false` und Neustart, `/sift debug intro reset`, Sift betreten → kein Klang.

## Schnüffler-Höhlen (ab alpha.28)

Nur in **neu generierten** Chunks. Nur mit Spieler:
- [ ] Neues Gelände in überwucherten Biomen oder im Ödland erzeugen (weit hinaus teleportieren, ein paar Minuten
      fliegen), dann `/sift debug landmarks` → „Schnüffler-Höhlen bekannt: …“ mit den drei nächsten.
- [ ] Klick auf **[Eingang]**: Am Boden öffnet sich ein Loch; ein gewundener Gang (14–16 Blöcke) führt etwa
      9 Blöcke hinab, Wände aus Gesundem und trockenem Sculk.
- [ ] Klick auf **[Nest]**: große Kammer, in der Mitte ein Nest aus Weidenlaub mit Pflanzen, manchmal ein
      Schnüffler-Ei. Daneben eine Nebenkammer mit einem kleinen Ichor-Becken (leuchtet), am Boden Fackellilien
      **oder** Kannenpflanzen.
- [ ] Im Nest stehen zwei erwachsene Schnüffler, manchmal ein Junges. Sie erscheinen, sobald die Chunks geladen
      sind (`/sift debug worldgen` → „Schnüffler erzeugt … von …“).
- [ ] Fackellilien und Kannenpflanzen auf Sift-Boden bleiben stehen, wenn daneben ein Block gesetzt oder abgebaut
      wird (Pflanzen in Höhlen, Flecken und am Ufer).
- [ ] An der Oberfläche: selten Flecken aus 5–9 Fackellilien oder Kannenpflanzen auf Sculk oder Bewuchs.
- [ ] Schalter: `worldgen.sniffer-caves.enabled: false` (oder nur `family: false`) bzw.
      `worldgen.sniffer-plant-patches.enabled: false`, Neustart, neues Gelände: keine Höhlen / keine Schnüffler /
      keine Flecken.

## Seelenschluchten (ab alpha.18, Phase 2 Schritt 6a)

Neue Schluchten entstehen nur in **neu generierten** Chunks. Für schon generiertes Gelände gibt es
`/sift debug canyon build`.
- [ ] In neuem Gelände (z. B. weit hinausfliegen oder Chunky in `the_sift:the_sift`): `/sift debug landmarks`
      zeigt „Seelenschluchten bekannt: N“ und die nächste Schlucht. Klick auf den Eingang teleportiert hin.
- [ ] Die Schlucht: ein diagonaler, leicht gewundener Graben (etwa 45 Blöcke lang, 7–10 breit). Vom Eingang
      führt eine Rampe ohne Stufen über 1 Block hinunter, bis 18–20 Blöcke tief; der Boden ist Siftschiefer.
- [ ] Im tiefen Teil liegen Seelenblöcke im Boden, als gewundene Ader und zwei kleine Taschen, mindestens 8.
- [ ] Über den Seelenblöcken steigen deutlich Sculk-Seelen auf (alpha.18.1: mehrere je Block, alle halbe Sekunde),
      sobald man in 48 Blöcken ist; weiter weg nicht. `/sift debug landmarks` an der Schlucht: Zeile „Seelen-Partikel“
      mit Schluchten in Sichtweite, Seelenblöcken, offenen, und „letzter Lauf: … Partikel“ größer 0.
- [ ] `worldgen.soul-canyons.particles.type: SOUL` (Neustart): kleinere blaue Seelen; `enabled: false`: keine.
      Die alte Form `particles: true` lädt ohne Fehler.
- [ ] Ein Seelenblock abbauen: droppt nichts (wie im Original); setzen und wieder abbauen ändert die Zahl
      „Seelenblöcke“ in `/sift debug landmarks`.
- [ ] Neben dem Haupt-Portal (34 Blöcke) und in verlassenen Portalen entsteht keine Schlucht.
- [ ] `/sift debug worldgen`: „Seelenschluchten: X von Y Kandidaten gebaut“, Zeit je Kandidat im Mittel unter
      etwa 20 ms (Smoke-Test: 15,7 ms, höchstens 35 ms).
- [ ] **In deinem Testgebiet:** auf flachem, kahlem Siftschiefer `/sift debug canyon build`. Erwartet:
      „Seelenschlucht gebaut“ mit Mitte, Zahl der Seelenblöcke und Teleport zum Eingang. Passt es nirgends in
      32 Blöcken, kommt eine Meldung mit den Gründen; dann an flacherer Stelle versuchen.
- [ ] Nach einem Neustart sind die Schluchten weiter in `/sift debug landmarks` (Markierungen gespeichert).
- [ ] `worldgen.soul-canyons.enabled: false` und Neustart: neue Chunks bekommen keine Schluchten mehr.

## Portale (ab Schritt 7a, Schreine und Schlüssel ab 7b)

Für Schritt 7b eine frische Welt (Hauptwelt, Nether, End) nehmen; Schreine entstehen nur in neuen Chunks.
Konsolen im Überlebensmodus testen: Im Kreativmodus zerbricht ein Schlag den Notenblock, und der Schlüssel wird
nicht verbraucht.

**Schreine finden**
- [ ] `/sift portal locate` in Hauptwelt, Nether und End (im End weit draußen): Klick teleportiert hin. Der
      Schrein steht:
  - Hauptwelt: Tiefenschiefer-Ruine mit Sculk, über Wasser auf Sockel.
  - Nether: Schwarzstein mit Seelenfeuer, kein Lavafluss hinein.
  - End: Purpur mit Endstäben, auf Insel oder eigener Insel.
  - Screenshots bitte.
- [ ] `/sift portal list`: Der Schrein steht als `shrine-…` mit Zustand LOCKED da.
- [ ] Sift-Kompass (`/sift give @s sift_compass` oder Rezept): Die Nadel zeigt auf den Schrein, die
      Actionbar nennt die Entfernung, die beim Hingehen sinkt. In der Sift zeigt er auf das Haupt-Portal,
      in einer Welt ohne Schreine dreht sich die Nadel.

**Schlüssel öffnen das Portal** (Issue #20, seit alpha.37 statt der Melodie)
```
/give @s heavy_core
/give @s echo_shard 4
/give @s sculk_catalyst
/sift give @s soul_ember_core
/sift give @s void_resonator
/sift give @s ancient_resonator
```
- [ ] Schlafende Konsole: Rechtsklick und Schlagen am Notenblock geben nur den verzerrten Klang und den Hinweis
      „Der passende Schlüssel öffnet das Portal“.
- [ ] Falscher Schlüssel (z. B. Seelenglut-Kern an der Ruine der Hauptwelt, Echo-Resonator an der Antiken
      Stadt): verzerrter Klang, Hinweis „passt nicht“, Schlüssel bleibt.
- [ ] Echo-Resonator herstellen (Schwerer Kern in der Mitte, 4 Echo-Splitter, Sculk-Katalysator unten) und per
      Rechtsklick auf eine Konsole der Ruine:
  - Die Konsolen erwachen nacheinander, von der angeklickten aus, mit Farbstoß.
  - Der Auslöse-Klang ertönt, dann spielen die Konsolen ihre Klänge mit Lichtsäulen nach.
  - Der Rahmen füllt sich vom Rand zur Mitte, die Nebelfläche erscheint, die Öffnung leuchtet (zusammen etwa
    15 s).
  - Der Schlüssel ist weg (Überlebensmodus); im Kreativmodus bleibt er.
  - Ein weiterer Rechtsklick mit Schlüssel: „Dieses Portal ist schon offen“, nichts wird verbraucht.
- [ ] Während des Öffnens noch einmal klicken: nichts passiert, kein zweiter Schlüssel weg.
- [ ] Dasselbe im Nether (Seelenglut-Kern) und im End (Leere-Resonator).
- [ ] Antike Stadt: nur der Antike Resonator öffnet sie.
- [ ] `portals.keys.consume: false` (Neustart): Schlüssel bleibt nach dem Öffnen im Inventar.
- [ ] Klangnummern (Konsolen im Noten-Modus, z. B. nach dem Öffnen oder an eigenen Konsolen):
  - Rechtsklick auf einen Notenblock schaltet den Klang weiter, immer in der Reihenfolge
    **1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 1**; die Actionbar zeigt „Klang N von 8“. Schlagen spielt ihn.
  - Redstone am Notenblock spielt den Sift-Klang ohne Anzeige.
- [ ] `/sift portal info` (Admin) listet die 8 Konsolen mit Klangnummer und den passenden Schlüssel.
- [ ] `/sift portal lock <id>` an einem offenen Portal: Fläche schließt, Konsolen schlafen wieder; der Schlüssel
      öffnet es erneut (und wird verbraucht).

**Eigene Konsolen außerhalb der Sift**
- [ ] Klingenden Tiefenschiefer aus `/sift items` in der Hauptwelt setzen (früher gesperrt), zwei direkt
      nebeneinander: Beide behalten ihr Aussehen. Andere Sift-Blöcke bleiben außerhalb der Sift gesperrt.
- [ ] Eigener Rahmen mit Konsolen: `/sift portal create ~ ~ ~`, dann mit dem Echo-Resonator öffnen wie oben.

**Durchgang**
- [ ] Hineingehen:
  - Man kommt im Haupt-Portal der Sift an, steht in der Öffnung und wird nicht sofort zurückgeschickt.
  - Hinaus und wieder hinein: zurück an die Einstiegsstelle (auch in Nether und End). Dort ebenso: erst
    hinaus, dann wieder hinein.
- [ ] Relog oder Neustart, während man in der Ankunfts-Öffnung steht: kein sofortiger Rücksprung.
- [ ] Tier hineinführen (Leine lösen) oder Item hineinwerfen: kommt in der Sift an.
- [ ] `/sift portal info`, `list`, `deactivate` (Fläche verschwindet von der Mitte aus), `activate`,
      `remove`. `deactivate` am Haupt-Portal der Sift wird abgelehnt.
- [ ] Nebelfläche aus der Nähe und von der Seite, in Grafik „Schnell“ und „Schön“ (Screenshot).

**Rezepte und Beute**
- [ ] Rezepte im Rezeptbuch (seit alpha.37 gestaffelt):
  - Echo-Resonator: Schwerer Kern, 4 Echo-Splitter, Sculk-Katalysator.
  - Seelenglut-Kern: 3 Wither-Skelett-Schädel, Netherit-Barren, 2 Ghast-Tränen, Lohenrute.
  - Leere-Resonator: Drachenkopf, Elytren, 2 Shulkerschalen, Geplatzte Chorusfrucht.
  - Sift-Kompass. Die Items glänzen und haben eigene Texturen. Die Kompassnadel dreht sich sauber.
- [ ] Antiker Resonator: kein Rezept im Rezeptbuch; eigene Textur (Auge in Tiefenschiefer-Tafel).
- [ ] Truhen in Antiker Stadt und Bastion: selten (3 %) der Schlüssel der Dimension; in der Antiken Stadt sehr
      selten (1 %) der Antike Resonator. In Endsiedlungen liegt kein Schlüssel mehr. Der normale Inhalt bleibt.

## Melodie je Portal (alpha.20 bis alpha.36)

Entfällt seit alpha.37: Portale öffnen nur noch mit dem Schlüssel (oben).

## Zugang zur Sift (ab alpha.20)

Zum Testen einen Spieler **ohne** Bypass nehmen (OP hat ihn: `lp user <Spieler> permission set
thesift.access.bypass false`, danach wieder entfernen).

- [ ] `/sift close` während du in der Sift bist: Chat „schließt in 30 Sekunden“, Actionbar zählt herunter,
      danach stehst du an der Stelle, an der du hinein bist (ohne Rückkehrpunkt, z. B. nach `/sift tp`: am
      Spawn der Hauptwelt) mit der Meldung „zurückgeschickt“.
- [ ] `/sift close`, dann vor Ablauf `/sift open`: Countdown bricht ab, niemand wird zurückgeschickt.
- [ ] Geschlossen durch ein offenes Portal gehen: Actionbar „Die Sift ist geschlossen“ (bei after-dragon /
      fixed-date mit Restzeit), man bleibt draußen. Tier hineinführen / Item hineinwerfen: bleibt draußen.
- [ ] Geschlossen: Schrein mit dem Schlüssel öffnen geht weiter, das Portal öffnet sich.
- [ ] Geschlossen: `/tp <Spieler>` zu jemandem in der Sift bzw. `/execute in the_sift:the_sift run tp …`
      wird abgebrochen (mit Meldung).
- [ ] In der Sift ausloggen, `/sift close` abwarten, einloggen: man wird zurückgeschickt.
- [ ] Bett/Respawnpunkt in der Sift, geschlossen sterben: Respawn am Spawn der Hauptwelt.
- [ ] Hinaus durch das Haupt-Portal geht auch geschlossen (Spieler mit Bypass drinnen, dann ohne testen).
- [ ] Mit Bypass (`lp user <Spieler> permission set thesift.access.bypass true`): hinein trotz geschlossen,
      kein Countdown, kein Zurückschicken.
- [ ] `/sift open`: Ankündigung im Chat an alle.
- [ ] Neustart nach `/sift close`: bleibt zu (`/sift access status`: „Befehl: geschlossen“); `/sift access
      reset` kehrt zum Modus zurück.
- [ ] `access.mode: after-dragon`, `/sift access reload`: zu, „wartet auf den ersten Drachenkill“. Drachen im
      End besiegen: `status` zeigt Zeit und Welt, Öffnung in `delay-days`. `/sift access set dragon-kill
      2026-01-01` (Vergangenheit): öffnet mit Ankündigung. `clear` löscht den Kill.
- [ ] `access.dragon-worlds: [eine_andere_welt]`: ein Drachenkill in `world_the_end` zählt nicht.
- [ ] Server, dessen Drache schon vor dem Plugin besiegt wurde, mit after-dragon: Warnung beim Start und in
      `status`, Sift bleibt zu, bis `set dragon-kill` gegeben wird.
- [ ] `access.mode: fixed-date`, `date` in zwei Minuten, `reload`: `status` zeigt die Restzeit; zum Zeitpunkt
      öffnet die Sift mit Ankündigung.
- [ ] `access.mode: manual`: zu, bis `/sift open`.

## Siftit-Erhalt beim Tod (ab alpha.23)

**Regression Hotfix alpha.34** (NPE bei jedem Tod mit Vanilla-Gegenständen). Jeweils in der Sift, Überlebensmodus,
`/gamerule minecraft:keep_inventory false` in der Sift, danach `/sift siftite list` und Log prüfen:
- [ ] Tod mit leerem Inventar (`/clear`, dann `/kill`): kein Fehler, nichts gesichert.
- [ ] Tod nur mit Vanilla-Gegenständen (`/give @s cobblestone 5`, `/give @s stick`): kein Fehler, alles droppt.
- [ ] Tod mit Sift-Gegenständen und Vanilla gemischt (`/sift give @s siftite_sword`, `/sift give @s siftite_ingot 3`,
      `/give @s bread 4`): Siftit kommt nach dem Respawn per Mini-Riss zurück, das Brot droppt; kein Fehler.
- [ ] Todesarten: Fallschaden (`/tp @s ~ ~60 ~`), Void (`/tp @s ~ -80 ~`), Mob (`/sift spawn sifter`, stehen bleiben).
- [ ] Todesbildschirm 10 s offen lassen, dann respawnen: Siftit kommt genau einmal zurück (`/sift siftite list` danach
      leer), im Log höchstens „Second death event … ignored“, keine Exception.

- [ ] Mit Siftit-Schwert, -Helm, 5 Barren und anderen Gegenständen sterben: Nur die anderen fallen; nach dem
      Respawn öffnet sich nach 2 s ein Mini-Riss, die Siftit-Gegenstände fliegen einzeln zu dir (Partikel),
      danach schließt er sich. Advancement „Keep Inventory“. Der Helm liegt im Inventar, nicht auf dem Kopf.
- [ ] Inventar voll beim Respawn: Die Gegenstände kreisen, Hinweis in der Actionbar; Platz schaffen → sie
      kommen.
- [ ] Während des Flugs ausloggen und wieder einloggen: Der Rest kommt erneut, nichts doppelt
      (`/sift siftite list <Spieler>` vorher und nachher vergleichen).
- [ ] Während des Flugs Server stoppen und starten: wie oben.
- [ ] Zweimal sterben, bevor alles zurück ist: alle Gegenstände beider Tode kommen zurück.
- [ ] `/gamerule keep_inventory true`: Vanilla behält alles, kein Mini-Riss.
- [ ] Mit einem Grab-Plugin (falls auf dem Server): Siftit landet nicht im Grab, sondern kommt zurück.
- [ ] `/sift siftite list <Spieler>` (online und offline) und `/sift siftite restore <Spieler>`: alles sofort
      im Inventar, die Liste ist leer, kein weiterer Mini-Riss.
- [ ] Totem der Unsterblichkeit: kein Tod, nichts wird gesichert.
- [ ] Hotfix alpha.23.1:
  - `keep_inventory` gilt je Welt. In der Sift `/execute in the_sift:the_sift run gamerule keep_inventory true`,
    dann `… false`, in der Sift sterben: Siftit wird gesichert.
  - Mit `true` nur in der Hauptwelt und Tod in der Sift: wird ebenfalls gesichert.
  - Mit `true` in der Sterbewelt: alles bleibt im Inventar, Log-Zeile „not saved: keep_inventory is on in …“.
  - `/sift siftite list` ohne Namen zeigt die eigenen Stapel und `keep_inventory` je Welt.

## Risse aus, Portal-Staffel (ab alpha.42a.2)

**Smoke-Test** (ohne Spieler):
- Bestehende config.yml mit `rifts.enabled: true` behält den Wert (Risse laufen); auf `false` gesetzt: Log „Rifts are
  off …; Siftite keep-on-death and its mini rift are independent of this“, `/sift rift` meldet „Risse sind aus“.
- Unit-Test `SiftiteKeeperIndependenceTest`: Die Klassen des Siftit-Erhalts verweisen auf keine Riss-Klasse und lesen
  keinen `rifts.`-Schlüssel; Standard `rifts.enabled: false`, `siftite.keep-on-death.enabled: true`, Mini-Riss-Modell im
  Pack.
- `access.mode: always`: Tag 0 wird beim Start festgehalten; `end` und `ancient-city` frei, `nether` ab Tag 7,
  `overworld` ab Tag 14 versiegelt.
- Ein offener Oberwelt-Schrein (`overworld` versiegelt): Ein Schwein in der Portalfläche bleibt in der Oberwelt. Nach
  `schedule unlock overworld` kommt ein neues Schwein am Haupt-Portal der Sift an. `auto`, `set-open 2026-11-01T12:00`
  (Nether frei), `shift 2` und `set-open clear` zeigen die erwarteten Daten.
- Unit-Test `PortalScheduleTest`: Gruppen, Tage, Overrides, Tag 0 je Zugangsmodus.

**Nur mit Spieler:**
```
/sift portal schedule
/sift portal info
/sift give @s echo_resonator
/sift give @s soul_ember_core
/sift give @s void_resonator
/sift give @s ancient_resonator
/sift give @s siftite_ingot 3
/sift give @s sift_rift
```
- [ ] **Siftit-Tod mit abgeschalteten Rissen** (`rifts.enabled: false`): mit Siftitbarren im Inventar sterben (z. B.
      `/kill @s`, keepInventory aus). Nach dem Respawn öffnet sich der Mini-Riss neben dir, die Barren fliegen einzeln
      heraus und landen im Inventar. Nichts geht verloren (`/sift siftite list <spieler>` danach leer).
- [ ] **Keine Risse:** eine Viertelstunde in der Sift: kein natürlicher Riss. Das Item `sift_rift`: Rechtsklick tut
      nichts außer der Meldung „Risse sind abgeschaltet“.
- [ ] **Versiegelter Schrein:** Oberwelt-Schrein mit Echo-Resonator (Gruppe `overworld`, Tag 14): Konsole glitcht,
      Meldung mit Datum und Restzeit, der Schlüssel bleibt in der Hand.
- [ ] **Frei am Tag 0:** End-Schrein mit Leere-Resonator und Portal der Antiken Stadt mit Antikem Resonator öffnen sich.
- [ ] **Offenes, aber versiegeltes Portal:** ein offenes Portal einer versiegelten Gruppe betreten: Meldung, kein
      Durchgang. `sift portal schedule unlock <gruppe>`, Portal verlassen und wieder betreten: Durchgang.
- [ ] **Bypass:** mit `thesift.access.bypass` öffnet der Schlüssel auch versiegelte Portale, und man geht durch.
- [ ] **Hinaus immer:** In der Sift durch das Haupt-Portal hinaus geht immer.

## Risse (ab alpha.22, seit alpha.42a.2 standardmäßig aus)

Für schnelle Tests `rifts.natural.first-delay-ticks: 200` und `interval-ticks: 400` setzen, danach zurück.
- [ ] Natürlicher Riss in der Sift erscheint 20–64 Blöcke entfernt, mit Klang beim Öffnen; leuchtende
      Fläche mit Oberwelt-Vorschau, öffnet sich animiert, Leuchtfeuer-Summen in der Nähe; leuchtet nachts.
- [ ] Hindurchgehen: Ankunft am eigenen Bett (bzw. Welt-Spawn) in einem Gegen-Riss mit Sift-Vorschau;
      Advancement „Rifter“. Zurück durch den Gegen-Riss: Ankunft am Ursprungs-Riss in der Sift.
- [ ] Nach der Ankunft wirkt der Riss erst nach dem Verlassen wieder (kein Hin und Her).
- [ ] Bett in einem Claim eines anderen Spielers (GriefPrevention/WorldGuard) bzw. im Spawn-Schutz: der
      Gegen-Riss öffnet außerhalb davon.
- [ ] Nach 5 Minuten schließen beide Risse (animiert); die Lichtblöcke sind weg (F3 bzw. Lichtniveau).
- [ ] Zeitsperre: `/sift close`, dann durch den Gegen-Riss in der Oberwelt: Meldung „geschlossen“, man
      bleibt draußen. Mit `thesift.access.bypass` geht es.
- [ ] Mobs laufen nicht freiwillig in einen Riss; ein hineingeschobenes Tier reist mit.
- [ ] Item `sift_rift` im Kreativmodus: Rechtsklick auf den Boden öffnet einen Riss (Oberwelt: in die Sift);
      ohne Platz: Hinweis. Im Überlebensmodus ohne Adminrecht passiert nichts.
- [ ] `/sift rift open`, `close`, `list` (Teleport-Klick).
- [ ] Schallplatte „Rift“ in eine leere Jukebox neben einem offenen Riss: Advancement „Rift Resonance“.
- [ ] Serverstopp mit offenem Riss: Nach dem Start sind dort keine Lichtblöcke mehr.
- [ ] Hotfix alpha.23.1:
  - Durch einen natürlichen Riss gehen, dann `/sift rift close` und zweimal `/sift rift open`: keine
    ConcurrentModificationException im Log.
  - `/sift rift open` in hügeligem Gelände der Sift: Der Riss öffnet am nächsten freien Platz.
  - Bett unter Bäumen: Der Gegen-Riss steht auf dem Boden, nicht über den Kronen.
  - Zweimal `/sift rift open` an derselben Stelle: Der zweite Riss steht daneben, nie darüber.
  - `/sift rift list` zeigt die Zähler; „Riss-Displays ohne Riss: jetzt 0“.

## Mobs: Charoit-Irrlicht (ab alpha.42d)

**Smoke-Test** (ohne Spieler): Selbsttest „Charoit-Irrlicht: 17 Würfel, Animationen idle/attack/blink, Name
„Charoit-Irrlicht“ / „Charoite Wisp““, „Brut-Zone 3: [charoite_wisp, dark_sniffer]“; alte Config `[dark_sniffer]` →
„old defaults replaced … (alpha42d)“. `/sift spawn charoite_wisp`: Name `{translate: "entity.the_sift.charoite_wisp"}`,
schwebt ohne Ziel auf 1,5 Blöcke über dem Boden; `/damage … 2` → 4 Blöcke weggeblinkt, zweiter Treffer in der
Abklingzeit ohne Blinken, 18 → 12 Leben. Brut von temple-1 (`spawners simulate`): Zone 3 wartet, bis die 16 Brut-Plätze
frei sind, dann 6 Irrlichter aus den vier Spawnern; Tod ohne Beute.

**Nur mit Spieler** (Überlebensmodus, in der Sift; einmal Client Englisch, einmal Deutsch):
```
/sift tp
/sift spawn charoite_wisp
/sift temple spawners clear temple-1
/sift temple spawners reset temple-1
```
- [ ] **Aussehen:** leuchtender türkiser Kern mit Auge, acht violette Splitter kreisen, grauer Ring dreht gegenläufig,
      Schweif pendelt; beim Schuss weiten sich die Splitter, beim Blinken schrumpft es weg.
- [ ] **Verhalten:** hält etwa 10 Blöcke Abstand, schwebt über einem, kreist; kommt man nah, weicht es zurück.
- [ ] **Splitter:** langsam, folgt einem leicht, weicht man seitlich aus, verfehlt er; trifft er, 2,5 Herzen; prallt an
      Wänden ab (verschwindet). Ohne Sichtlinie schießt es nicht.
- [ ] **Blinken:** Pfeil oder Schlag → es springt ein paar Blöcke weg (Partikel, Klang), ein zweiter Treffer kurz danach
      lässt es stehen.
- [ ] **Brut:** In Zone 3 (innerer Ringgang) kommen Irrlichter und Dunkle Schnüffler gemischt; keine Beute, keine
      Erfahrung; weggehen → verschwinden; nie mehr als 16 Brut-Mobs.
- [ ] **Zu zweit:** mehr Mobs je Welle, die Irrlichter teilen sich auf beide auf; schwer, aber machbar.
- [ ] **Todesmeldung:** von einem Splitter getötet → „… was slain by Charoite Wisp“ (englischer Client) bzw.
      „… wurde von Charoit-Irrlicht erschlagen“ (deutscher Client).
- [ ] **Friedlich:** greift nicht an.

## Mobs: Allays (ab alpha.31)

Nur mit Spieler (der Spawner arbeitet nur in der Nähe von Spielern in der Sift):
- [ ] Im Überwucherten Wald stehen bleiben (`/execute in the_sift:the_sift positioned 0 100 0 run locate biome
      the_sift:overgrown_forest`, Teleport-Klick), ein bis zwei Minuten warten. `/sift debug allays` → Gruppen und
      Allays steigen; Klick auf **[Teleport]** führt zu ihnen: 2–4 Allays schweben etwa 5–12 Blöcke über dem Boden.
- [ ] Im Sift-Ödland Gruppen von 2–3, in der Lichtung 1–3, in anderen Biomen (z. B. Siftschiefer-Gipfel) keine.
- [ ] Nach längerem Warten bleibt die Zahl im Umkreis begrenzt (Wald höchstens 16).
- [ ] Schalter `mobs.allay.enabled: false`, Neustart: keine neuen Allays.

## Mobs: Blub (ab Phase 2)

Vorher in `config.yml` nichts ändern; die Werte stehen unter `mobs.blub`. Resource Pack neu laden (neue
Modelle). Test in der Sift, Überlebensmodus, wo nicht anders angegeben.

**Aussehen und Animation**
- [ ] `/sift spawn blub` (oder Blub-Spawn-Ei aus `/sift items`, Rechtsklick auf einen Block): Der Blub
      sieht aus wie im Original (Kopf mit Ohren, vier Beinchen), Textur sauber, keine Lücken zwischen den
      Teilen. Screenshot aus der Nähe und von der Seite.
- [ ] `/sift spawn blub` mit Blick schräg nach unten oder oben: Der Blub steht trotzdem gerade.
      `/sift debug mobs` zeigt in der Zeile „Rotation“: Display-Entity Yaw 0 und Pitch 0, Modell-Pitch
      etwa 2,5° (Kopfneigung des Originals), Roll 0°, Modell-Yaw = 180 − Körper-Yaw.
- [ ] Er hüpft, statt zu laufen: Sprünge mit kurzer Pause am Boden, Beine und Ohren schwingen mit
      (Animation „hopping“), schneller beim Angriff.
- [ ] Im Stand flattert er ab und zu mit den Ohren.
- [ ] Treffer: Das Modell blinkt kurz rot. Tod: Es kippt zur Seite wie ein Vanilla-Mob und verschwindet.
- [ ] Bei wenig Leben hängen die Ohren.
- [ ] Aus 48 Blöcken Entfernung ist das Modell noch zu sehen, weiter weg nicht. Kein Flackern beim
      Drehen und Hüpfen (falls doch: `mobs.render.interpolation-ticks` auf 4 setzen und melden).

**Zähmen, Sitzen, Füttern**
- [ ] Seelenblock in der Hand: Der Blub kommt hinterher (bis 10 Blöcke), ohne Seelenblock nicht.
- [ ] Rechtsklick mit Seelenblock: verbraucht 1 Seelenblock; Herzen = gezähmt (etwa jedes dritte Mal),
      Rauch = nicht. Beim Zähmen dreht er sich zu dir, zirpt und macht die Freuden-Animation (Kopf dreht
      sich einmal ganz), die Textur wird zur gezähmten. Advancement „Blub“ erscheint.
- [ ] Gezähmt: folgt dir, teleportiert zu dir, wenn du weit weg bist; Rechtsklick (ohne Seelenblock)
      lässt ihn sitzen (Sitzhaltung: Kopf tiefer, Beine angewinkelt) bzw. wieder folgen.
- [ ] Gezähmt und verletzt: Seelenblock oder Essen (z. B. Brot) per Rechtsklick heilt 4 Leben.
- [ ] Gezähmt: verteidigt dich und greift an, was du schlägst; greift keine anderen Blubs, fremde
      gezähmte Tiere oder Spieler im Kreativmodus an.

**Rudelzorn**
- [ ] Mehrere ungezähmte Blubs (`/sift spawn blub 4`), einen schlagen: Alle im Umkreis von 16 Blöcken
      greifen an (Schaden 3), nach etwa 30 s hören sie auf. Im Kreativmodus greifen sie nicht an.
- [ ] Während des Zorns lässt sich kein Blub zähmen.

**Klänge**
- [ ] Zirpen im Stand (an Land und im Wasser anders), eigener Treffer-, Angriffs- und Todesklang aus dem
      Pack. Kein Wolfsheulen, -knurren oder -hecheln. Untertitel zeigen „Blub …“.

**Basis-Mob schlägt nicht durch** (unsichtbarer Wolf)
- [ ] Kein Wolfsklang, kein Wolfsmodell, kein Halsband, kein Schatten eines Wolfs (nur der kleine des
      Blubs).
- [ ] Knochen, Fleisch oder Farbstoff auf einen ungezähmten bzw. gezähmten Blub: kein Zähmen, kein
      Halsband färben, keine Wolfsrüstung, keine Vermehrung (zwei gezähmte Blubs mit Fleisch füttern).
- [ ] Der Blub jagt keine Schafe, Kaninchen, Füchse oder Skelette und flieht nicht vor Lamas.
- [ ] Leine, Boot, Lore: geht nicht mit einem Blub.
- [ ] Tod durch einen Blub: Die Todesnachricht nennt „Blub“, nicht „Wolf“.

**Spawnen und Grenzen**
- [ ] In den Überwucherten Biomen tauchen gelegentlich Gruppen von 1–3 Blubs auf (32–76 Blöcke entfernt,
      höchstens 4 im Umkreis). In anderen Biomen und in der Hauptwelt nicht.
- [ ] `/sift debug mobs` zeigt Anzahl je Welt und Art, Displays, Transformationen pro Sekunde und den
      nächsten Mob (Hitbox etwa 0,66 × 0,94).
- [ ] Neustart und Chunk neu laden (weggehen und zurückkommen): Blubs sind wieder da, mit Modell, ohne
      doppelte oder verwaiste Modellteile (`/execute if entity @e[type=item_display,distance=..30]`
      zählt 7 je Blub).
- [ ] Gezähmter Blub geht durch ein offenes Portal mit (Modell ist danach wieder da).

## Mobs: Namen und Treffer-Färbung (alpha.19.2)

- [ ] Todesnachrichten nennen den Sift-Mob, nicht den Basis-Mob: von einem Sifter getötet → „… wurde von Sifter
      erschlagen“ (nicht „Schwein“), ebenso Dunkler Schnüffler (nicht Eisbär). Ein gezähmter Blub, der von einem Sifter
      getötet wird: „Blub wurde von Sifter erschlagen“. `/kill` auf einen Sift-Mob meldet seinen Namen.
- [ ] Über keinem Sift-Mob erscheint ein Namensschild (auch nicht beim Anschauen aus der Nähe).
- [ ] Sifter gegen Blub (gewollt, wie im Original): Ein Sifter greift von sich aus nur Spieler an. Greift ein
      gezähmter Blub ihn an (er verteidigt dich), schlägt der Sifter zurück und kann den Blub töten.
- [ ] Treffer-Färbung: Blub treffen → das ganze Modell färbt sich kurz gleichmäßig rötlich und wird nach einer halben
      Sekunde wieder normal; kein Schachbrett-Muster. `/sift debug mobs` direkt danach: „Färbung: Treffer, 7 von 7
      Displays“, danach „normal, 0 von 7“, „fremde Displays dieses Mobs: 0“. Sieht er doch gescheckt aus: bitte die
      Zeile „Färbung“ notieren (fremde Displays > 0 wären die Ursache, sie werden binnen 5 s entfernt).

## Mobs: Modellteile beim Chunk-Entladen (Hotfix alpha.17)

Gilt für alle Sift-Mobs. Die Modellteile (Item-Displays) werden nie gespeichert und tragen die Markierung
`the_sift:rig`; beim Entladen werden sie nicht mehr mitten im Entlade-Vorgang entfernt, sondern einen Tick
später.
- [ ] Einige Sift-Mobs aller Arten an einer Stelle erzeugen, weggehen (Chunks entladen) und zurückkommen:
      keine Fehler in der Konsole, jedes Modell genau einmal da (`/execute if entity @e[type=item_display,distance=..30]`
      zählt Würfel × Mobs: Blub 7, Sifter 10, Singer 10, Dunkler Schnüffler 7).
- [ ] Dasselbe mit Neustart statt Weggehen; und mehrmals schnell hin und her (Elytra) über die Chunk-Grenze.
- [ ] Einen Sift-Mob töten oder mit `/kill` entfernen: Seine Modellteile verschwinden sofort mit ihm (nach
      einem Tick), es bleibt nichts in der Luft stehen.
- [ ] `/sift debug mobs` zeigt „verwaiste entfernt“: Modellteile ohne ihren Mob, die beim Laden eines Chunks
      gefunden und gelöscht wurden. In einer Welt, die nur mit alpha.17 oder neuer lief, bleibt der Wert 0.
- [ ] Auf einer Welt aus einer älteren Version: Chunks mit früher stehengebliebenen Modellteilen laden →
      die Teile verschwinden, der Zähler steigt.
- [ ] `mobs.blub.enabled: false` und Neustart: kein Spawnen, `/sift spawn blub` meldet „abgeschaltet“;
      vorhandene Blubs bleiben als unsichtbare Wölfe stehen, bis der Schalter wieder an ist.

## Mobs: Sifter (ab Phase 2, Schritt 2)

Test in der Sift, Schwierigkeit mindestens Leicht, Überlebensmodus. Werte unter `mobs.sifter`.

**Aussehen und Animation**
- [ ] `/sift spawn sifter` bzw. Sifter-Spawn-Ei: Modell wie im Original (Körper, Kopf mit Zähnen, vier
      Beine), steht gerade. `/sift debug mobs`: Hitbox 0,90 × 0,90, HP 40, 10 Displays.
- [ ] Beim Laufen spielt die Lauf-Animation (schnell), im Stand nicht.
- [ ] Biss: Kopf schnappt zu, der Klang kommt kurz vorher, der Schaden etwa eine halbe Sekunde nach
      Beginn. Wer vorher zurückweicht (mehr als 2,5 Blöcke), wird nicht getroffen.

**Kampf**
- [ ] Er bemerkt dich in bis zu 28 Blöcken, wenn er dich sieht, und jagt dich (etwas schneller als ein
      Zombie). Hinter einer Wand verliert er dich nach ein paar Sekunden.
- [ ] Biss: 10 Schaden auf Normal (mit Rüstung weniger), etwa alle 1,5–2 Sekunden.
- [ ] Im Kreativ- oder Zuschauermodus ignoriert er dich.
- [ ] Wer ihn trifft (auch ein anderer Mob), wird sein Ziel.
- [ ] Tod durch einen Sifter: Die Todesnachricht nennt „Sifter“.

**Beute und Erfahrung**
- [ ] Getötet: 1–2 Rohes Sifterfleisch, mit Plünderung III bis zu 5. Brennend getötet (Feueraspekt):
      gebratenes Sifterfleisch. Erfahrung nur, wenn ein Spieler ihn tötet.

**Friedlich**
- [ ] Schwierigkeit der Sift auf Friedlich (`/execute in the_sift:the_sift run difficulty peaceful`):
      Alle geladenen Sifter verschwinden, es spawnen keine neuen. Wieder auf Normal: Sie spawnen erneut.

**Spawnen**
- [ ] In der Sift erscheinen Gruppen von 1–5 Sifter in 32–76 Blöcken Abstand, nur auf Siftschiefer,
      Siftschiefer-Bewuchs und gesundem Sculk, meist an der Oberfläche, manchmal in Höhlen.
- [ ] Am meisten im Überwucherten Wald (bis 8 im Umkreis), weniger in den übrigen überwucherten Biomen
      (6), in der Ödnis und sonst (5). Alle Sift-Mobs zusammen höchstens 8 im Umkreis von 64 Blöcken
      um dich.
- [ ] In der Hauptwelt spawnen keine Sifter.

**Basis-Mob schlägt nicht durch** (unsichtbares Schwein)
- [ ] Kein Grunzen, kein Schweinemodell, kein Schatten eines Schweins.
- [ ] Sattel, Karottenrute, Karotten (Locken, Füttern, Vermehren): keine Wirkung.
- [ ] Blitz (Dreizack mit Kanalisierung bei Gewitter): kein Zombifizierter Piglin.
- [ ] Er flieht nicht bei Schaden und folgt keinem Spieler mit Karotte.
- [ ] Im Wasser oder bei Tageslicht: keine Verwandlung, kein Brennen.

## Mobs: Singer und Antike Stadt (ab Phase 2, Schritt 3)

Für die Antike Stadt eine **frische Welt** nehmen (nur neue Chunks bekommen das Portal). Werte unter
`mobs.singer` und `portals.ancient-city`. Konsolen im Überlebensmodus testen.

**Singer rufen (Schrein)**
- [ ] Ziegenhorn in bis zu 40 Blöcken von einem schlafenden Schrein blasen: Nach etwa 3 Sekunden steigt der
      Singer im Portal aus dem Boden (Partikel, Klang), zur nächsten Konsole gedreht.
- [ ] Er singt, im weiten Umkreis zu hören. Acht Schallwellen (türkise Partikel) laufen nacheinander zu den
      Konsolen; jede leuchtet auf, wenn ihre Welle ankommt (Farbstoß), **wacht aber nicht auf** (seit alpha.37).
- [ ] Danach versinkt er wieder im Boden und ist weg. Spieler in der Nähe lesen „Die Konsolen hallen nach und
      schlafen weiter. Nur der passende Schlüssel öffnet dieses Portal.“; `/sift portal info` zeigt „0/8 wach“,
      Zustand LOCKED.
- [ ] `mobs.singer.summon.enabled: false` (Neustart): Das Ziegenhorn ruft keinen Singer mehr.
- [ ] Ein zweites Horn während des Gesangs ruft keinen zweiten Singer. An einem wachen oder offenen Portal
      erscheint keiner.
- [ ] Während des Auftritts ist er nicht zu verletzen und nicht wegzuschieben.
- [ ] Mit `portals.keys.enabled: false` (Neustart) weist die Konsole den Schlüssel ab („Schlüssel sind
      abgeschaltet …“), nur `/sift portal activate` öffnet; die Schlüssel liegen dann nicht mehr in Truhen.

**Bleibender Singer und Tausch**
- [ ] `/sift spawn singer` oder Singer-Spawn-Ei: Er steigt aus dem Boden und bleibt. Er läuft gemächlich in
      einem Umkreis von etwa 8 Blöcken um den Ort, an dem er erschien, und nie in Gänge unter 4 Blöcken.
- [ ] Rechtsklick mit Seelenblock: Er nimmt ihn, hält ihn hoch (Animation), freut sich und wirft dir nach
      2 Sekunden eine Belohnung zu (z. B. Eisenblöcke, Antiker Schrott, Schmiedevorlage, Saatgut, Wolle).
      Advancement „Absolutes Gehör“. Ein fallen gelassener Seelenblock in der Nähe wird ebenfalls geholt.
- [ ] Sitzt er fest (z. B. eingemauert), versinkt er nach etwa 10 Sekunden im Boden.
- [ ] Er ist verwundbar (20 HP), gibt 8–12 Erfahrung, wenn ein Spieler ihn tötet, keine Beute.
- [ ] Zombies und Plünderer greifen ihn nicht an.

**Basis-Mob schlägt nicht durch** (unsichtbarer Fahrender Händler)
- [ ] Rechtsklick öffnet kein Handelsmenü, kein Händlerklang, keine Lamas.
- [ ] Er trinkt nachts keinen Unsichtbarkeitstrank und abends keine Milch, verschwindet nicht nach einer
      Weile, folgt keinem Ziel, flieht nicht vor Zombies.
- [ ] Keine lila Partikel (deshalb kein Enderman als Basis, siehe Abweichungen).

**Antike Stadt**
- [ ] `/locate structure minecraft:ancient_city` in der neuen Welt, hinfliegen: Im Zentrum steht statt des
      Vanilla-Rahmens das Portal des Originals mit 8 Konsolen; auf den Konsolen Notenblöcke (wo Kerzen
      standen, sind sie ersetzt). Log: „Ancient City portal city-… registered“. `/sift portal list` zeigt
      `city-…` LOCKED.
- [ ] Seit alpha.37: Der Echo-Resonator passt nicht („passt nicht“, bleibt im Inventar). Der Antike
      Resonator (`/sift give @s ancient_resonator`) öffnet es mit dem ganzen Ablauf; Durchgang führt in die Sift
      und zurück.
- [ ] Truhen der Antiken Stadt: sehr selten (1 %) der Antike Resonator; kein Buch „Lied der Tiefe“ mehr.
- [ ] `portals.ancient-city.enabled: false` und Neustart: neue Antike Städte haben wieder das Vanilla-Zentrum.

## Mobs: Dunkler Schnüffler (ab Phase 2, Schritt 4)

Test in der Sift, Schwierigkeit mindestens Leicht, Überlebensmodus. Werte unter `mobs.dark_sniffer`.

**Aussehen und Animation**
- [ ] `/sift spawn dark_sniffer` oder Spawn-Ei: eigenes, vereinfachtes Modell (Körper, Kopf, Schnauze, vier
      Beine) mit der Original-Textur, steht gerade, keine durchsichtigen Löcher. Screenshot bitte.
      `/sift debug mobs`: Hitbox 1,90 × 1,90, HP 40, 7 Displays.
- [ ] Gehen: Beine schwingen; Biss: Kopf schnappt; vor dem Schallangriff hebt er den Kopf (Schnüffeln);
      beim Graben senkt er den Kopf und scharrt.

**Kampf**
- [ ] Er bemerkt dich in bis zu 32 Blöcken (auch ohne Sicht) und jagt dich; Biss 7 Schaden auf Normal, etwa
      jede Sekunde, aus bis zu gut 3 Blöcken.
- [ ] Bleibst du außer Reichweite (mindestens 5 Blöcke, in Sicht): Er bleibt stehen, schnüffelt kurz, eine
      Schallwelle (Wächter-Partikel) fliegt zu dir und **zieht dich zu ihm heran**, ohne Schaden. Höchstens
      alle 5 Sekunden.
- [ ] Im Kreativ- oder Zuschauermodus ignoriert er dich. Auf Friedlich greift er nicht an (bleibt aber da).
- [ ] Normale Schnüffler in der Nähe greift er an; sie fliehen vor ihm (Umkreis 20).
- [ ] Getötet: 1 Sculk, Erfahrung nur bei Spieler-Kill. Todesnachricht nennt „Dunkler Schnüffler“.

**Graben**
- [ ] Ohne Ziel neben Sculk: Er geht nach einer Weile zu einem Sculk-Block, gräbt (Partikel, Klang) und lässt
      Sculkblumensamen fallen (Advancement „Riecht unheimlich“). Danach etwa 8 Minuten Pause. Der Sculk bleibt.

**Infektion**
- [ ] Schnell und reproduzierbar: In der Sift einen normalen Schnüffler erzeugen (`/summon sniffer` oder
      Spawn-Ei), danebenstellen und `/sift debug infect`. Er bekommt Seelen-Partikel und wird nach 30 Sekunden
      zum Dunklen Schnüffler (Klang, Log-Zeile „became a Dark Sniffer“). Er behält seine Lebenspunkte (ein
      Schnüffler hat 14, also 14 von 40), wie im Original.
- [ ] Natürlich: Schnüffler graben jetzt auch auf Sift-Sculk (Datapack ergänzt `#minecraft:sniffer_diggable_block`
      wie das Original). Prüfen mit `/execute if block <x y z> #minecraft:sniffer_diggable_block` auf Sculk bzw.
      Gesundem Sculk → „Test passed“. Einen Schnüffler auf eine freie Sculk-Fläche stellen (mindestens 5 × 5,
      kein Gras): Er schnüffelt und gräbt nach 1–4 Minuten von selbst, dann wie oben. Im Smoke-Test hat ein
      per `/summon sniffer` erzeugter Schnüffler auf Sculk nach etwa 4 Minuten gegraben und sich verwandelt.
- [ ] In der Hauptwelt passiert beides nicht (`infection.only-in-sift`); `/sift debug infect` markiert ihn
      dort zwar, die Verwandlung bleibt aber aus.

**Wächter sind Freunde** (`wardens-friendly`, `wardens-pet`)
- [ ] `/summon warden` neben einem Dunklen Schnüffler (Schnüffler läuft herum): Der Wächter wird nicht wütend
      auf ihn (`/data get entity @e[type=warden,limit=1] anger` bleibt leer bzw. ohne ihn), greift ihn nicht an.
      Andere Mobs (z. B. ein Zombie) und Spieler greift er wie gewohnt an.
- [ ] Wächter-Angriffe treffen ihn nicht: `/damage <Schnüffler> 10 minecraft:sonic_boom by <Wächter>` → HP
      bleibt 40 (`/sift debug mobs`).
- [ ] Streicheln: Ein ganz ruhiger Wächter geht nach 40–120 Sekunden zu einem Dunklen Schnüffler in 18 Blöcken,
      bleibt etwa 3 Blöcke davor stehen und streichelt ihn 5 Sekunden lang (Herz-Partikel über dem Schnüffler,
      der stillhält und den Wächter ansieht). `/sift debug mobs` zeigt dabei beim Schnüffler „Zustand: wird von
      einem Wächter gestreichelt“. Danach 2–4 Minuten Pause.
- [ ] `wardens-friendly: false` → Wächter greifen ihn wieder an (Vanilla-Verhalten), kein Streicheln.

**Spawnen**
- [ ] In einer frisch generierten Sculk-Region an der Oberfläche der Sift stehen 1–2 Dunkle Schnüffler auf dem
      Sculk. Sie bleiben dort (verschwinden nicht, entfernen sich höchstens 100 Blöcke von dort).

**Basis-Mob schlägt nicht durch** (unsichtbarer Eisbär)
- [ ] Kein Eisbärbrummen, kein Warnlaut, keine Eisbär-Schritte; nur Schnüffler-Klänge.
- [ ] Er richtet sich nie auf (auch nicht vor dem Angriff) und schützt keine Jungen; Fische und Füchse lassen
      ihn kalt.
- [ ] Fisch füttern, Vermehren, Leine: geht nicht.

## Mobs: Echo Golem und Seelen-Singer (ab alpha.19, Phase 2 Schritt 6b)

Test in der Sift, Überlebensmodus. Werte unter `mobs.echo_golem`, Seelen-Singer unter `mobs.singer.soul-event`.
`/sift debug golems` zeigt alle Golems mit Zustand und die Zähler; `/sift debug mobs` den nächsten.

**So testest du gezielt (ohne neue Chunks)**
1. Auf flachen, kahlen Siftschiefer stellen (Gamerule `mob_griefing` an) und `/sift debug canyon build`. Die Meldung
   nennt Mitte und Eingang (Klick = Teleport) und trägt den Eingang als „Golem ausstehend“ ein.
2. Innerhalb von etwa 4 s erscheint dort von selbst ein Golem (`/sift debug golems`: „1 an Schlucht-Eingängen“). Oder
   gezielt: am Eingang `/sift spawn echo_golem`.
3. Am Rand der Schlucht zuschauen (nicht hineinspringen, ein Treffer lässt ihn die Seele fallen). Er läuft die Rampe
   hinunter, bückt sich über einem Seelenblock, der Block verschwindet, und er klettert mit dem leuchtenden Block
   wieder hinauf.
4. Oben ruft er einen Seelen-Singer; der kommt heran, der Golem verbeugt sich, der Block wechselt den Besitzer
   (Advancement „Like father and son“ in 16 Blöcken). Danach geht das Spiel von vorn los.
5. Binden: einem Golem mit einem Echo-Splitter (Rechtsklick) begegnen. Ab dann bringt er die Seele dir.

**Prüfpunkte**
- [ ] Aussehen: Modell des Originals (Körper, Arme, Kopf mit „Krone“), läuft und steht gerade; beim Tragen
      sitzt der Seelenblock oben auf dem Kopf und leuchtet, sonst ist er unsichtbar.
- [ ] Animationen: Gehen, Bücken beim Aufheben, Tragen (Gehen und Stehen), Verbeugen, Sitzen.
- [ ] **Rampe:** Er kommt die Rampe hinunter bis zu den Seelen und mit Seele wieder hinauf, ohne hängen zu bleiben
      (Smoke-Test: 8 Runden in 4 Minuten, y 85 → 73 → 88).
- [ ] Aufheben: Der Seelenblock wird zu Luft; `mob_griefing false` oder `pickup.enabled: false` → er hebt nichts auf.
- [ ] Seelen-Singer: erscheint 7–12 Blöcke vom Golem, nicht direkt an der Schlucht; nimmt den Block; taucht nach
      etwa 30 s ohne Golem wieder ab. Ein Singer aus dem Spawn-Ei nimmt Golem-Blöcke ebenfalls an.
- [ ] Echo-Splitter (alpha.19.1): Rechtsklick mit dem Splitter in der Haupt- **oder** Nebenhand bindet einen ungebundenen
      Golem in jedem Zustand (sucht, trägt, sitzt): Herzen, Klang, Splitter verbraucht (nicht im Kreativmodus).
      `/sift debug golems` zeigt danach „gebunden an <Name>“. Ein tragender Golem behält dabei seine Seele. Auf einem
      schon gebundenen Golem passiert mit dem Splitter nichts, er bleibt in der Hand. Nach einem Neustart ist er noch
      gebunden.
- [ ] Rechtsklicks auf andere Sift-Mobs wirken wieder (Blub mit Seelenblock zähmen, Sitzen, Singer-Tausch per Rechtsklick).
- [ ] **Übergabe an dich (alpha.19.2)**, im Überlebens- oder Abenteuermodus, ohne ihn anzuklicken: Er kommt heran,
      verbeugt sich, der Seelenblock landet im **Inventar**, Advancement „Friend of the Sift“, Klang; das Leuchten
      erlischt. `/sift debug golems`: „letzte Übergabe: … ins Inventar übergeben an <Name> (Verbeugung)“.
- [ ] Dasselbe mit **vollem Inventar**: Der Block liegt dir vor den Füßen (nur du kannst ihn aufheben), Hinweis in der
      Actionbar, Advancement trotzdem; Debug: „Inventar voll, vor die Füße gelegt“.
- [ ] **Rechtsklick** (ohne Splitter) auf deinen gebundenen, tragenden Golem: sofortige Übergabe wie oben, er lässt
      den Block **nicht** fallen. Mit `mobs.echo_golem.bonded.right-click-gives: false` (Neustart): wie im Original,
      er lässt ihn fallen und setzt sich.
- [ ] **Kreativ- oder Zuschauermodus**: Er bringt dir nichts (behält den Block, patrouilliert); Rechtsklick bewirkt
      nichts. Zurück im Überlebensmodus kommt er wieder zu dir. Weiter als 64 Blöcke weg: Er läuft um den Eingang.
- [ ] Ein Treffer lässt den Seelenblock als Item fallen; er setzt sich 30 s hin (`/sift debug golems`: „zuletzt
      fallen gelassen: Schaden … durch …“). Rechtsklick eines anderen Spielers auf einen gebundenen Golem: nichts.
      Ein **ungebundener** Golem lässt den Block bei Rechtsklick weiter fallen.
- [ ] Tod: keine Beute, Erfahrung 4–7 nur bei Spieler-Kill; trug er einen Block, fällt der vorher heraus.
- [ ] Erscheinen um Spieler: weit weg von jeder Schlucht fliegen; nach einer Weile erscheint 82–160 Blöcke entfernt
      ein Golem, solange in 165 Blöcken keiner ist.
- [ ] Grenzen: Golems zählen zu `mobs.limits.per-world`; bei erreichter Grenze erscheinen keine weiteren.

**Lichtblock (sicheres Aufräumen)**
- [ ] Nachts in der Schlucht: Ein tragender Golem erhellt seine Umgebung (Lichtstufe 14), das Licht wandert mit.
      F3 + Blick auf den Kopfblock: `minecraft:light`. Unter Wasser oder in Pflanzen gibt es kein Licht.
- [ ] `/sift debug golems` an der Schlucht: „Lichtblöcke … in 48 Blöcken: N, davon von tragenden Golems N, übrig 0“.
- [ ] Weggehen (Chunks entladen), Server neu starten, zurückkommen: „übrig 0“, kein Licht an alten Stellen.
- [ ] Golem mit Seele töten (`/kill`): Licht ist sofort weg.
- [ ] Einen eigenen Lichtblock (`/give @s light`) in die Nähe setzen: Er bleibt immer stehen (zählt als „übrig“).
- [ ] `glow.light-block: false` (Neustart): kein Lichtblock mehr, das Modell leuchtet weiter (`glow.model`).

**Basis-Mob schlägt nicht durch** (unsichtbares Schaf)
- [ ] Kein Blöken, keine Schaf-Schritte; Schere (auch aus einem Werfer), Farbstoff, Weizen, Leine: nichts passiert.
- [ ] Er frisst kein Gras und lässt keine Wolle fallen.

## Boss-Items (ab alpha.39)

Überlebensmodus, Schwierigkeit mindestens Leicht.
```
/sift give @s warden_heart
/sift give @s dragon_scale
/sift give @s wither_core
/sift give @s golem_trophy
/summon warden ~ ~ ~5
/give @s netherite_sword
/give @s soul_sand 4
/give @s wither_skeleton_skull 3
```
- [ ] Die vier Items: eigene Textur, lila Name (episch), Glanz, stapelbar bis 16. Deutsche Namen „Herz des Wächters“,
      „Drachenschuppe“, „Kern des Withers“, „Nachtkristall“ (bis alpha.41.1 „Golem-Trophäe“).
- [ ] Wächter selbst töten: Herz des Wächters liegt in den Drops.
- [ ] Wächter per `/kill @e[type=warden]`: kein Herz.
- [ ] Wither bauen (Seelensand + 3 Schädel) und töten: Kern des Withers in den Drops.
- [ ] Enderdrache (mit zwei Spielern, beide treffen ihn): Jeder bekommt eine Drachenschuppe ins Inventar (Actionbar
      „Du erhältst: Drachenschuppe“). Ein Spieler ohne Treffer bekommt keine. Drache mit 4 Endkristallen wiederbeleben
      und erneut töten: wieder je eine.
- [ ] `phase4.boss-items.wither-core.cooldown-hours: 24` (Neustart): zweiter Wither am selben Tag gibt keinen Kern.
- [ ] Als Zutat: in `portals.recipes` z. B. beim Leere-Resonator `E: "the_sift:warden_heart"` eintragen, Neustart:
      Rezeptbuch zeigt das Herz; keine Warnung „recipe … in config.yml“ im Log.

## 3D-Item-Modelle (ab alpha.40.2)

Neues Release mit dem Resource Pack nötig.
```
/sift give @s warden_heart
/sift give @s wither_core
/sift give @s dragon_scale
/sift give @s golem_trophy
/sift give @s charoite_core
/give @s item_frame 5
/sift debug enchanter
```
- [ ] **Inventar:** Alle fünf Items als kleine 3D-Modelle, schräg von oben wie Blöcke.
  - Herz: Sculk-Herz mit dunklem Kern.
  - Wither: schwarzer Block mit Augen und Zähnen.
  - Schuppe: dicke violette Platte.
  - Trophäe: seelenblaues Kristallbündel auf Sockel mit Silberring.
  - Kern: Charoit-Kugel im Silber-Gimbal.
- [ ] **Hand:** Erste und dritte Person (F5), rechte und linke Hand. Die Items sind sichtbar und nicht zu groß.
- [ ] **Boden:** Fallen lassen, das Modell dreht sich klein am Boden.
- [ ] **Item-Rahmen:** Die Vorderseite zeigt nach vorn (Augen des Wither-Kerns sichtbar).
- [ ] **Sculk Enchanter:** Über dem Block dreht sich ein facettiertes violettes Kristallbündel mit drei kleinen
      Nebenkristallen, kein flaches Oval. Es liest sich von allen Seiten.
- [ ] **Unverändert:** Schlüssel, Kompass und das Sculk-Enchanter-Item (Block-Würfel).

## Sculk Enchanter (ab alpha.40, eigener Block ab alpha.40.1)

Überlebensmodus. Neues Release mit dem Resource Pack nötig.
```
/sift give @s charoite 64
/sift give @s warden_heart
/sift give @s dragon_scale
/sift give @s wither_core
/sift give @s siftite_ingot
/sift give @s sculk_enchanter
/give @s enchanting_table
/give @s diamond_pickaxe
/xp add @s 100 levels
/give @s diamond_sword[enchantments={sharpness:5,unbreaking:3,mending:1}]
/give @s diamond_pickaxe[enchantments={fortune:3,efficiency:5}]
```
- [ ] **Charoit-Kern:** im Rezeptbuch (5 Charoit, Herz, Schuppe, Kern, Siftitbarren), eigene Textur, lila Name.
- [ ] **Ritual:**
  - Zaubertisch setzen, Kern in die Hand, unter 30 Leveln Rechtsklick: Hinweis.
  - Mit genug Leveln: Partikel, Klang, Kern und 30 Level weg.
  - Der Tisch wird zum **eigenen Block**: oben violette Rune, Seiten mit leuchtend seelenblauem Gravurband.
  - Darüber dreht sich langsam ein violetter Charoit-Kristall, Sculk-Seelen steigen auf.
- [ ] **Item:** „Sculk Enchanter“ zeigt den Block als Würfel (Inventar, Hand, Rezeptbuch). Gesetzt, auch in der
      Oberwelt, ist es sofort ein Enchanter mit Kristall.
- [ ] **Menü** (Rechtsklick):
  - Schwert einlegen: Knöpfe Schärfe VI und Haltbarkeit IV, keiner für Reparatur.
  - 8 Charoit rechts, Klick: Schärfe VI, 8 Charoit und 15 Level weg. Dann Schärfe VII für 16 Charoit und 30 Level,
    danach kein Knopf mehr.
- [ ] **Grenzen:**
  - Höchstens 2 Verzauberungen über dem Maximum.
  - Spitzhacke: Glück nur bis IV, Effizienz bis VII.
  - Ohne genug Charoit oder Level ist der Knopf rot.
- [ ] **Menü schließen:** Gegenstand und Charoit kommen zurück.
- [ ] **Abbau:** Mit der Diamantspitzhacke abbauen, droppt „Sculk Enchanter“, der Kristall verschwindet. Von Hand: lange,
      ohne Drop. Neu setzen: wieder Enchanter mit Kristall.
- [ ] **Kristall:** Weit weggehen (Chunk entladen) und zurückkommen: genau ein Kristall. Nach einem Neustart ebenso.
- [ ] **Amboss:** Schärfe VII + Buch Schärfe V ergibt weiter Schärfe VII.
- [ ] **Abschalten:**
  - `phase4.enchanter.floating-crystal: false` + Neustart: kein Kristall.
  - `phase4.enchanter.block-style: table` + Neustart: Ritual und Item ergeben einen markierten Zaubertisch (wie alpha.40).
- [ ] **Bedrock (falls Geyser im Einsatz):** Ein Bedrock-Spieler sieht einen roten Pilzblock; Rechtsklick öffnet trotzdem
      das Menü.

## Tempel (ab alpha.41a, Bastion ab alpha.41.1)

**Smoke-Test alpha.41.1** (frische Sift, alle 10 Tempelflächen komplett per Forceload, je 7 × 7 Chunks):
- 10 von 10 Tempeln eingetragen (nach 15 s), keine wartenden Teile übrig.
- **Zeit:** 250 Chunk-Teile in der Weltgenerierung, im Mittel 0,67 ms, höchstens 4,5 ms; 240 Teile, deren Chunk vor der
  Tempelmitte entstand, auf dem Main-Thread nachgebaut, im Mittel 2,9 ms, ein Ausreißer 29 ms (1 Teil je Tick, kein
  „Can't keep up“).
- `/sift temple inspect` für alle 10: 5372 Säulen auf dem Hof, **0 schwebend**; fremde Blöcke über dem Hof bei 9 Tempeln
  0, bei einem 2 Pflanzen auf einem Mauerstück. Bodenhöhen 83 bis 168 (temple-7 liegt im Gebirge und ist frei geräumt).
- Alle 10 Enchanter mit genau einem Kristall, alle 20 Truhen mit `the_sift:chests/temple`.
- **Schutz:** Endkristall vor der Nordmauer: Mauer und Hof bleiben gleich (1716 Blöcke verglichen); nach `defeat` nicht
  mehr. Kolben, Abbau und Bypass wie in alpha.41a.
- **Neu bauen:** der alte 41a-Tempel temple-1 der Testwelt mit `/sift temple rebuild temple-1` → Version 2, 49 Chunks
  in rund 5 s (1 je Tick), danach 0 schwebend, 0 fremd; ein zweites `rebuild` auf eine Bastion ebenso.
- **Im Test gefunden und behoben:**
  - Das Fundament richtete sich nach der Heightmap; eine Höhle direkt unter dem Hof blieb offen. Jetzt füllt es bis zum
    ersten festen Block auf.
  - Pflanzen schwebten über dem Hof: Die Worldgen-Heightmap kennt keine von Features gesetzten Pflanzen, das Räumen
    endete zu tief. Jetzt wird jede Säule bis über die Turmspitzen geräumt.
  - Die Pflanzenfeatures der Sift wachsen auf jedem Pilzstiel-Zustand (aller Siftschiefer); Nachbar-Chunks streuten sie
    auf den Hof. Der Hofboden ist deshalb Vanilla-Tiefenschiefer, und in Tempel-Chunks laufen die übrigen Schritte des
    Sift-Populators nicht mehr.
  - Die Tempel-Loot-Tabelle lag in `pack-src/datapack`, das packgen bei jedem Import neu schreibt; sie liegt jetzt in
    `pack-src/datapack-overlay`.
- Unit-Tests: Bauplan mit Markern, versiegelten Prüfungsräumen, Größe aus der Config und einem Weg zu Fuß vom Tor zu
  Arena, Enchanter, Truhen, Wehrgang und Kern.

**Nur mit Spieler** (Überlebensmodus, ohne `thesift.temple.bypass`; OP reicht nicht als Ausnahme; neues Release mit
Resource Pack für Siftit- und Charoit-Block):
```
/sift temple sites
/sift temple list
/sift temple inspect temple-1
/give @s diamond_pickaxe
/give @s tnt 8
/give @s flint_and_steel
/give @s piston 2
/give @s redstone_block
/sift give @s charoite 64
/sift give @s siftite_ingot 9
/xp add @s 100 levels
```
- [ ] **Alter Tempel auf dem Testserver:** `sift temple list` zeigt die 41a-Tempel mit „(alter Bau, rebuild)“.
      `sift temple rebuild temple-1` (Konsole): Meldung mit Zahl der entfernten Blöcke und Dauer; der alte Tempel ist weg,
      die Bastion steht, kein Rest vom alten Bau (Säulen, Kammern) im Hof.
- [ ] **Hinkommen:** Teleport-Klick in `/sift temple list` (landet vor dem Tor im Süden).
- [ ] **Aussehen** (bitte 2–3 Screenshots, auch von oben):
  - Ringmauer mit Wehrgang und Zinnen, Strebepfeiler, vier runde Ecktürme und drei Mauertürme mit Charoit-Wimpeln und
    Bannern aus Charoit- und Siftit-Block, Torhaus mit halbem Fallgitter, zerfallene Kasernen.
  - Spalte mit Seelenfeuer um den erhöhten Kern, vier ansteigende Brücken, Obelisken mit Charoit-Spitze.
  - In der Mitte der offene Schacht hinab zur Arena; unten der Altar, Seelenlaternen an Ketten.
  - Der Tempel schwebt nicht und steckt nicht im Berg; ringsum eine Böschung.
- [ ] **Begehbar:**
  - Treppen vom Hof auf den Wehrgang, Türen vom Wehrgang in die Türme, Türen vom Hof in die Türme.
  - Über eine Brücke auf den Kern, von dort in den Schacht schauen.
  - Eine der vier Treppen vom Hof hinab in die Prüfungsebene: äußerer Ringgang, Pfeilerkammern mit Ichorglas-Licht,
    Speichen zum inneren Ring, durch eins der vier Tore in die Arena zum Altar.
  - Schatzhalle im Süden unten: Siftit-Säulen mit Charoit-Kapitell, Enchanter auf dem Podest (Kristall), zwei Truhen.
  - Die Prüfungsräume im Osten und Westen sind zu (Charoit-Siegel), kein Weg hinein.
- [ ] **Truhen:** Beute aus Charoit, Siftit-Nuggets, Ichor usw.; die zweite Truhe hat andere Beute.
- [ ] **Schutz:** Mauer, Hofboden, Treppe, Laterne und Truhe abbauen: geht nicht, Actionbar-Hinweis. TNT an der Mauer:
      die Mauer bleibt (Gelände ringsum darf kaputtgehen). Kolben gegen die Mauer: fährt nicht aus.
- [ ] **Altar:** Rechtsklick auf den Seelenblock: „Der Seelen-Altar schweigt noch.“
- [ ] **Enchanter gesperrt:** Rechtsklick: Hinweis, dass er erst nach dem Sieg erwacht, kein Menü.
- [ ] **Nach dem Sieg** (Konsole `sift temple defeat temple-1`):
  - Enchanter öffnet das Menü und steigert wie ein hergestellter.
  - Mauern und Truhen lassen sich abbauen; Enchanter, Altar und die Wände der Prüfungsräume weiterhin nicht.
  - `sift temple reset temple-1`: wieder geschützt, Enchanter wieder gesperrt.
- [ ] **Bypass:** `lp user <spieler> permission set thesift.temple.bypass true`: Mauer lässt sich abbauen. Danach wieder
      entfernen.
- [ ] **Neue Blöcke:** 9 Siftitbarren → Siftit-Block, 9 Charoit → Charoit-Block, jeweils zurück zu 9; eigene Texturen
      (blau-silberne Platten, violett gemaserter Charoit). Mit der Spitzhacke abbaubar, droppen sich selbst.
- [ ] **Neu erkunden:** einen noch nicht generierten Tempel anlaufen (`/sift temple sites` zeigt „noch nicht generiert“):
      Beim Näherkommen kein Ruckeln; der Tempel steht vollständig, keine fehlenden Chunk-Stücke. Danach
      `sift temple inspect <id>`: 0 schwebend, wartende Teile 0.

### Brut-Spawner (ab alpha.42a)

**Smoke-Test** (ohne Spieler, mit `/sift temple spawners simulate temple-1 2 900`):
- `rebuild temple-1` → Version 3, 12 Spawner (3/5/4 je Zone) als Prüfungsspawner mit blauen Flammen.
- Erste Welle sofort, 16 lebende Brut (Grenze je Tempel); Spawner über der Grenze warten (zuerst sprangen sie ohne Kampf
  durch ihre Wellen; behoben).
- Brut in Runden getötet: Wellen 1 → 2 → 3, nach 7 Runden alle 12 erschöpft (dunkel). Keine Beute, keine Erfahrung.
- `reset` → wach (blau); `defeat` → alle OFF, Brut weg, dunkel, übersteht den Neustart; Tempel-`reset` → frisch;
  `spawners clear` → erschöpft bis zum Reset. Übrig gebliebene Brut verschwindet beim Neustart.
- Frische Sift: alle 10 Tempel als Version 3 mit Spawnern, 0 schwebend, 0 fremd; 0,58 ms je Chunk-Teil in der
  Weltgenerierung (höchstens 4 ms), Nachbau auf dem Main-Thread 2,7 ms im Mittel.
- Ein Vanilla-Prüfungsspawner ohne Mob-Daten fällt nach 45 s auf `inactive` zurück und bleibt dort, auch mit `ominous`;
  keine Mobs, keine Beute. Nur mit einem Spieler daneben noch nicht geprüft (siehe unten).

**Nur mit Spieler** (Überlebensmodus, ein Tempel mit Version 3, sonst `sift temple rebuild <id>`):
```
/sift temple spawners temple-1
/sift temple spawners reset temple-1
```
- [ ] **Prüfungsspawner bleibt ruhig:** Neben einem Spawner stehen: Vanilla spawnt nichts, wirft keine Beute aus, der
      Block behält blaue Flammen, solange er wach ist.
- [ ] **Kasernen:** In eine Kaserne gehen: Klang, Sifter erscheinen um den Spawner, Welle 2 nach dem Sieg über Welle 1,
      danach erschöpft (dunkel, Rauch, Klang).
- [ ] **Prüfungsebene:** Pfeilerkammern (Sifter und Schnüffler, 3 Wellen), innerer Ring (Schnüffler) entsprechend.
- [ ] **Zu zweit:** Mehr Mobs je Welle; nie mehr als 16 Brut-Mobs gleichzeitig.
- [ ] **Keine Farm:** Brut-Mobs lassen nichts fallen und geben keine Erfahrung.
- [ ] **Weggehen:** Mitten in einer Welle 60 Blöcke weggehen, 30 s warten: die Brut verschwindet; zurück: der Spawner
      fängt wieder bei Welle 1 an.
- [ ] **Altar-Gate:** Rechtsklick auf den Altar, solange Spawner wach sind: „Der Seelen-Altar schweigt, solange N
      Brutkerne wachen.“; nach `sift temple spawners clear temple-1`: wieder der normale Text.
- [ ] **Schutz:** Spawner abbauen: geht nicht (bis zum Sieg). Nach `sift temple defeat temple-1`: alle dunkel, keine Brut.
- [ ] **Abschalten:** `phase4.temples.spawners.enabled: false` + Neustart: keine Brut, Altar ohne Gate.

### Tempel-Wege (ab alpha.42e, Layout-Version 4)

**Begehbarkeit** (rein, ohne Welt: `TempleWalk`): Ein Körper von 3 × 3 × 3 geht vom Tor aus; Stufen höchstens 1 hoch oder
tief, nie ein Sprung hinab, auch diagonal. Er muss den Hof, alle 12 Spawner (Zone 3 mit 4 hoch für das Irrlicht), die Arena
am Altar (4 hoch), beide Truhen und den Enchanter erreichen. Gemeldet werden auch Treppenstufen ohne Anschluss und
Stellen, an die nur ein Spieler passt. Unit-Tests: Standardgröße mit drei Seeds und fünf weitere erlaubte Größen (Radius
40 bis 64, Tiefe 16 bis 24, Mauern 10 bis 21) ohne Befund; kleine Testgitter für 1 breite Gänge und Treppen ohne Absatz.

**Vorher (Version 3, Bauplan):** Zone 2 und 3, Arena, Truhen und Enchanter für 3 × 3 × 3 nicht erreichbar, 34 Treppenstufen
ohne Anschluss (Wandtreppen 1 breit gegen die Mauer, Geländerstufen der Brücken), über 5000 Stellen nur für Spieler.
Ursachen: Die Treppen hinab liefen über die Verbindungsgänge und endeten am Fels, die Decke über ihnen stieg ab;
Speichen und Ring zu schmal für die Diagonale, Turmtüren 1–2 breit, Spawner mitten im Durchgang.

**Smoke-Test** (ohne Spieler, temple-1):
- `/sift temple validate temple-1` am alten Bau (Version 3): „Mängel“, 13 Ziele nicht erreichbar, 36 Treppenstufen ohne
  Anschluss, 4460 Engstellen (182 ms).
- `/sift temple rebuild temple-1` (48356 alte Blöcke, 49 Chunks, 5 s), danach `validate`: „in Ordnung“, Version 4, alle
  Ziele erreichbar, keine Treppe ohne Anschluss, keine Engstelle (211 ms). `inspect`: 0 schwebend, 0 fremd.
- `sift debug selftest`: „Tempel-Wege (Layout-Version 4, Radius 40): … alle Ziele erreichbar …“, bestanden.
- `spawners simulate temple-1 3 300`: 16 Brut-Mobs aus allen Zonen, keiner erstickt (Log ohne „suffocated“).
- Ein Tempel mit Version 3 behält seine Spawner an den alten Stellen (`spawners temple-2`).

**Nur mit Spieler** (ein Tempel mit Version 4: `/sift temple list`, sonst `/sift temple rebuild <id>`):
```
/sift temple validate temple-1
/sift temple spawners reset temple-1
/sift spawn dark_sniffer
```
- [ ] **Rundgang:** Vom Tor über den Hof, eine der vier Treppen hinab (bei x ±31), unten geradeaus und durch die Tür in die
      Pfeilerkammer, über die Speiche in den Ringgang (Zone 3), durch ein Tor in die Arena zum Altar, in die Nordkammer
      und in die Schatzhalle (Truhen, Enchanter). Nirgends hängen bleiben, nirgends springen müssen, keine Treppe endet an
      einer Wand.
- [ ] **Wehrgang:** Die Treppen an der Ost- und Westmauer (3 breit) hinauf, über den Absatz auf den Wehrgang, durch die
      Turmtüren (3 × 3).
- [ ] **Schnüffler im Gang:** Einen Dunklen Schnüffler in einen Gang der Prüfungsebene und auf eine Treppe locken
      (`/sift spawn dark_sniffer` dort): kein Erstickungsschaden, er folgt um Ecken und die Treppe hinauf.
- [ ] **Brut:** Spawner wecken: Schnüffler und Irrlichter erscheinen frei im Raum, nicht in Wänden; Irrlichter haben im
      Ringgang Platz über den Köpfen.
- [ ] **Optik:** Die offenen Treppenschächte im Hof mit Geländer, die Türen der Pfeilerkammern, die Spawner unter den
      Kammersäulen sehen stimmig aus.

### Advancements ohne Risse (ab alpha.42h)

**Smoke-Test:** Datapack lädt ohne Fehler (das neue Kriterium nutzt das 26.x-Format `type` / `all_of`). `sift debug
selftest`: „Advancements erreichbar: 21 von 23“, Hinweis „planting_the_dark (Anbau der Sculkblume fehlt noch);
rifter (Funktion aus)“, bestanden.

**Nur mit Spieler** (Rift-Schallplatte aus einer Truhe eines verlassenen Portals oder `/sift give <name> music_disc_rift`,
Plattenspieler):
- [ ] **Riss-Resonanz:** Schallplatte in der Sift in einen Plattenspieler legen → Advancement „Riss-Resonanz“ kommt; der
      Text lautet auf Englisch „Play the Rift Music Disc in a jukebox in The Sift“, auf Deutsch „Spiele die
      Rift-Schallplatte in einem Plattenspieler in der Sift“. In der Oberwelt: kein Advancement.
- [ ] In der Advancement-Ansicht steht „Riss-Resonanz“ unter der Wurzel „The Sift“ und ist sichtbar, ohne dass
      „Rissgänger“ erreicht ist.
- [ ] **Besserer Verzauberer:** am Sculk Enchanter eine Verzauberung mit Charoit anheben.
- [ ] **Nahrungskette:** ein gezähmter Blub besiegt einen Dunklen Schnüffler; der Besitzer bekommt es.
- [ ] **Lied der Vergangenheit:** Ziegenhorn im Zentrum einer Antiken Stadt blasen (nach „Untergegangene Zivilisation“).

### Boss-Balance und ein Altar je Spieler (ab alpha.42f)

**Unit-Tests:** Lebenspunkte für 1 bis 8 Spieler (700 / 1225 / 1750 / 2275 / 2800, ab 5 gedeckelt), Schaden ×1,0 bis ×1,4,
Helfer +0 / +0 / +1 / +1 / +2, Angriffsabstand ×1,0 bis ×0,8, Vorwarnzeiten unverändert. Bindung: erstes Auslösen
bindet, nach Abbruch derselbe Tempel wieder, anderer Tempel gesperrt, nach dem Sieg alles gesperrt, Mitkämpfer frei,
Bypass und Schalter binden niemanden, Admin-Reset und Spieler-Reset heben auf, übersteht den Neustart. Texte: Schlüssel
und Platzhalter in `messages.yml` und `messages_en.yml` gleich (MessagesLangTest).

**Smoke-Test** (ohne Spieler):
- Alte Config mit 600 / 0,5 / 4 und 14 / 8 / 10: „old defaults replaced … (alpha42f)“, danach 700 / 0,75 / 5 und
  15,4 / 8,8 / 11; neuer Abschnitt `scaling` und `phase4.temples.altar-one-per-player` ergänzt.
- `sift debug selftest`: „Großer Echo-Golem je Spieler – 1: 700 LP ×1.00 Schaden +0 Helfer … 5: 2800 LP ×1.40 Schaden +2
  Helfer“, „Ein Altar je Spieler: an, 0 Bindungen; Texte Deutsch und Englisch vorhanden“, bestanden.
- `boss start temple-1 3` → „falls (3 players, 1750 health)“; `boss stop` → abgebrochen, keine Bindung (Admin-Befehl).
- `temple altar status <unbekannt>` → „ist diesem Server unbekannt“ (kein Web-Lookup).

**Nur mit Spieler** (zwei Spieler A und B, zwei schlafende Tempel mit erschöpften Spawnern: `/sift temple spawners clear <id>`,
je 8 Seelenblöcke):
```
/sift temple altar status <A>
/sift temple altar reset <A>
/sift temple spawners clear temple-1
/sift temple spawners clear temple-2
```
- [ ] **Bindung:** A löst den Altar von temple-1 aus → `altar status A`: an temple-1 gebunden. B kämpft mit, löst nicht aus →
      `altar status B`: nicht gebunden.
- [ ] **Abbruch:** A stirbt bzw. alle verlassen die Arena (30 s) → Abbruch; A darf temple-1 erneut auslösen.
- [ ] **Zweiter Tempel gesperrt:** A am Altar von temple-2: keine Reaktion, Actionbar „Du hast deinen Tempel schon gewählt:
      temple-1“ (englischer Client: „You have already chosen your temple: temple-1“), Seelenblöcke bleiben in der Hand.
- [ ] **Nach dem Sieg:** temple-1 besiegen → A am Altar von temple-2: „Du hast deinen Tempel schon besiegt (temple-1)“;
      B (Mitkämpfer) bekam Beute und kann temple-2 auslösen.
- [ ] **Bypass:** Mit `thesift.temple.bypass` löst A temple-2 aus, `altar status A` bleibt bei temple-1.
- [ ] **Reset:** `altar reset A` → A darf wieder einen Tempel wählen.
- [ ] **Skalierung allein:** fordernd, aber machbar (700 Leben, Faust 15,4).
- [ ] **Zu zweit und zu dritt:** spürbar zäher (1225 / 1750 Leben), mehr Schaden, Angriffe etwas öfter; zu dritt 3
      Seelenhelfer in der Schallphase.

### Großer Echo-Golem (ab alpha.42b.1)

**Smoke-Test** (ohne Spieler, temple-1, mit `/sift temple boss start temple-1 1` und `boss damage`):
- Start → 5 s Countdown → „falls (1 players, 600 health)“; nach 8 s gelandet auf dem Landepunkt (-1543.5 71 626.5 =
  6 nördlich vor dem Altar, Arenaboden). 33 Displays (13 Modell, 12 Platten, 8 Charoit-Teile), Fehler 0.
- `damage 50 ranged` → abgeprallt, 600/600. 12 × `damage 5` → je Platte 20, nach der 12. Phase SCHALL bei 360/600.
- Seelenruf: 2 Seelenhelfer; ohne Störung brachten sie in ~30 s 6 Seelen (6 Platten), dann verschwanden sie.
- Helferplatte: Fernkampf prallt ab, Nahkampf bricht sie ohne Schaden. `damage 300` → WUT bei 60/600; `damage 100000` →
  60 abgezogen, besiegt; Tempel „besiegt“, Golem und Helfer weg.
- Start ohne Testhilfe und ohne Spieler in der Arena → nach dem Countdown abgebrochen, Tempel schläft.
- 2 bzw. 3 simulierte Spieler → 900 bzw. 1200 Leben. `boss stop` → Golem weg, schläft. Chunks entladen → sofort
  abgebrochen. Neustart mitten im Kampf → „called off“, nach dem Start schläft der Tempel, kein Golem, keine Displays.

**Smoke-Test alpha.42b.2** (ohne Spieler): `sift debug selftest` bestanden, 23 von 23 Advancements, darunter
`the_sift:the_sift/great_echo_golem`; „Stille im Tempel“ im Pack. `boss loot 8` → z. B. „netherite_chestplate,
mending 1, unbreaking 3, protection 4“, „netherite_sword, unbreaking 2, looting 1, sharpness 3“ (alle Stufen in den
Spannen). Helfer mit Pause: ungestört 6 Platten nach ~40 s (vorher ~30 s). Sieg mit Effekt ohne Fehler, danach keine
Mobs übrig.

**Nur mit Spieler** (Überlebensmodus, ein Tempel mit Version 3; zu zweit für die Skalierung und die Beute):
```
/sift temple list
/sift temple spawners clear temple-1
/sift give @s soul_block 16
/sift give @s minecraft:bow
/sift temple boss info temple-1
/sift temple reset temple-1
```
- [ ] **Altar:** Ohne Seelenblöcke: „Der Seelen-Altar verlangt 8 Seelenblöcke …“. Solange Spawner wach sind: Altar-Gate-Text.
      Mit 8 in der Hand: verbraucht, Countdown in der Actionbar, Glocke.
- [ ] **Landung:** Der Golem fällt durch den Schacht, landet vor dem Altar mit Stampfer (Stoß, kein Schaden). Aussehen:
      violetter Koloss, 12 Seelenplatten, Charoit-Krone und Schulterkristalle, Nachtkristall in der Brust. Bossleiste.
- [ ] **Größe:** Er passt nicht durch die Tore und bleibt in der Arena, kommt nach Ausweichen wieder zur Mitte.
- [ ] **Panzer:** Pfeil prallt ab (Hinweis, Schildklang); jeder Schlag bricht eine Platte (Glasklang, Splitter), man
      bekommt einen Seelenblock, die Bossleiste sinkt. Nach der 12. Platte: Brüllen, „Der Panzer ist gebrochen!“.
- [ ] **Angriffe:** Faustschlag in der Nähe (≈ 7 Herzen auf ungeschütztem Spieler), Stampfer mit rotem Ring 1 s vorher (springen
      → kein Schaden), Schallwelle ab Phase 2 auf Abstand (Aufladen hörbar, ausweichbar).
- [ ] **Seelenruf:** Zwei Helfer laufen zum Seelenring, nehmen eine Seele (leuchten), bringen sie dem Golem: neue Platte.
      Helfer schlagen: Seele fällt (verschwindet), er holt neu. Helfer töten geht.
- [ ] **Wut:** unter 25 % rötlich, schneller, Helfer verschwinden.
- [ ] **Seelengitter:** Während des Kampfes durch ein Tor hinaus: zurückgesetzt, Hinweis. Enderperle hinaus: geht nicht.
      Ein zweiter Spieler kann von außen hinein und kämpft mit (Bossleiste).
- [ ] **Nur aus der Arena:** Vom Kern oder aus dem Ringgang schießen: „Der Golem spürt nur Treffer aus der Arena.“
- [ ] **Sieg:** Titel, Seelen- und Kristallsplitter, Todesschrei. Für jeden, der genug Schaden machte oder am Ende in
      der Arena stand: Chat „Du erhältst: einen Nachtkristall, <Netherit-Teil> (verzaubert)“, beides im Inventar
      (Verzauberungen im Tooltip), Advancement „Stille im Tempel“ (Herausforderung). Wer unter 5 % blieb und am Ende
      draußen war (tot): nichts. Tempel besiegt, Enchanter frei, Kompass zeigt den nächsten Tempel. Erneuter
      Altar-Klick: „Der Wächter … ist besiegt.“
- [ ] **Volles Inventar:** Beute fällt vor die Füße, nur der Besitzer kann sie aufheben.
- [ ] **Bossleiste:** zeigt „Panzer, N Platten“ und zählt mit; in Phase 2 mit Helferplatten „Schall, N Platten“.
- [ ] **Abbruch:** Alle sterben bzw. 30 s niemand in der Arena → „zieht sich zurück“, Golem weg, Tempel schläft,
      Seelenblöcke weg. Erneut starten geht.
- [ ] **Tod im Kampf:** Respawn außerhalb, kein Zurücksetzen in die Arena; zurücklaufen und wieder mitkämpfen.
- [ ] **Abschalten:** `phase4.bosses.great-echo-golem.enabled: false` + Neustart: Altar meldet „schweigt noch“.

### Kompass und Gerüchte-Zettel (ab alpha.41b)

Smoke-Test ohne Spieler (Truhen per Trichter geleert, Chance zum Test auf 1,0): Tempeltruhe von temple-1 → „etwa 1100
Blöcke im Südosten“ (temple-10, der eigene Tempel ist ausgelassen); Truhe eines verlassenen Portals 68 Blöcke südlich von
temple-1 → „etwa 100 Blöcke im Norden“; alle 10 besiegt → „Alle Tempel sind verstummt.“; Tempel-Tabelle in einer Truhe
der Oberwelt → kein Zettel. Neue Config-Schlüssel kommen beim Start in eine bestehende `config.yml`.

**Nur mit Spieler** (in der Sift, neues Release mit Resource Pack):
```
/sift give @s sift_compass
/sift give @s temple_rumor
/sift temple list
```
- [ ] **Kompass umschalten:** Schleichen + Rechtsklick: Klang, Actionbar „Tempel: N Blöcke“, die Nadel zeigt auf den
      nächsten Tempel. Noch einmal: „Portal: N Blöcke“ (Hauptportal). Kein Leitstein wird gebunden, kein Block benutzt.
- [ ] **Hinlaufen:** Der Nadel folgen, die Entfernung sinkt, man kommt am Tempel an (Mitte der Arena).
- [ ] **Besiegte überspringen:** Konsole `sift temple defeat <id des nächsten>`: nach spätestens 2 s zeigt die Nadel auf
      den nächsten schlafenden. Danach `sift temple reset-all confirm`.
- [ ] **Modus bleibt:** Kompass ablegen, wieder aufnehmen, ausloggen und einloggen: weiter im Tempel-Modus. In der
      Oberwelt zeigt er auf Schreine („Portal: …“).
- [ ] **Zettel:** Truhen verlassener Portale öffnen (frisch generiert): ab und zu „Gerücht vom Tempel“ mit eigener
      Textur, Tooltip mit Richtung und Entfernung. Richtung und Entfernung passen ungefähr zum Kompass.
- [ ] **Abschalten:** `phase4.temples.compass.enabled: false` + Neustart: Schleichen + Rechtsklick tut nichts.
      `phase4.temples.hints.enabled: false` + Neustart: keine neuen Zettel.
- [ ] **Abschalten:** `phase4.temples.enabled: false` + Neustart: `/sift temple list` meldet „Tempel sind aus“, neue
      Chunks bekommen keine Tempel (bestehende bleiben stehen, ohne Schutz).

## Overworld unberührt

- [ ] Riesenpilz per Knochenmehl wachsen lassen: sieht aus wie Vanilla, auch die Kappenwände (seit alpha.41.2; vorher sahen
      rote Kappenwände teils wie Sift-Blöcke aus). Smoke-Test: `/sift debug mushrooms` an 64 roten und 64 braunen
      Riesenpilzen sowie in Pilzland und Dunklem Wald: 0 Pilzblöcke in Sift-Zuständen.
- [ ] Ein Sculk Enchanter aus alpha.40.1–41.1 (Oberwelt) ist nach dem Update weiter ein Enchanter mit Kristall (der
      Block wird beim Laden des Chunks auf den neuen Zustand umgestellt).
- [ ] Mehrere Pilzstiele/Pilzblöcke nebeneinander setzen: Innenseiten wie Vanilla (Hinweis: einzelne
      Kombinationen können wie Sift-Blöcke aussehen, siehe `docs/ADMIN.md`, Einschränkungen).
- [ ] Stolperdraht-Falle mit Haken und Faden: löst aus wie Vanilla.
- [ ] Chorus-Pflanze im End per Chorusblüte wachsen lassen, Stamm abbauen: bricht ab wie Vanilla.
- [ ] Fackeln, Wasser, Redstone neben Pilzblöcken in der Overworld: normales Verhalten.

## Versionsprüfung (optional)

- [ ] Plugin auf einem Paper-Server mit anderer Minecraft-Version: Server startet, Log meldet
      „TheSift … is built for Minecraft 26.3, but this server runs …“, keine Sift-Welt.
