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

## Blöcke

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
- [ ] Menge: nach dem Vorgenerieren ist etwa jeder siebte bis achte Kandidat gebaut; die meisten anderen
      stehen als „nicht ausgewählt (chance 0.25)“ in `/sift debug portals`.
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

## Overworld unberührt

- [ ] Riesenpilz per Knochenmehl wachsen lassen: sieht aus wie Vanilla.
- [ ] Mehrere Pilzstiele/Pilzblöcke nebeneinander setzen: Innenseiten wie Vanilla (Hinweis: einzelne
      Kombinationen können wie Sift-Blöcke aussehen, siehe `docs/ADMIN.md`, Einschränkungen).
- [ ] Stolperdraht-Falle mit Haken und Faden: löst aus wie Vanilla.
- [ ] Chorus-Pflanze im End per Chorusblüte wachsen lassen, Stamm abbauen: bricht ab wie Vanilla.
- [ ] Fackeln, Wasser, Redstone neben Pilzblöcken in der Overworld: normales Verhalten.

## Versionsprüfung (optional)

- [ ] Plugin auf einem Paper-Server mit anderer Minecraft-Version: Server startet, Log meldet
      „TheSift … is built for Minecraft 26.3, but this server runs …“, keine Sift-Welt.
