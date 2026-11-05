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
- [ ] Eimer an einem leuchtenden Block der Oberfläche (F3 zeigt `light` im Ziel): Danach ist dort Luft, kein
      unsichtbarer Lichtblock mehr. Dasselbe mit einem Schwamm am Ufer.
- [ ] Quelle über `/sift debug ichor` [Teleport] ansteuern: Ichor fließt aus einer Felswand, die Quelle leuchtet.
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
Melodie und Konsolen im Überlebensmodus spielen: Im Kreativmodus zerbricht ein Schlag den Notenblock.

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

**Schlüssel und Melodie** (Issue #20)
- [ ] Schlafende Konsole: Rechtsklick und Schlagen am Notenblock geben nur den verzerrten Klang und einen
      Hinweis.
- [ ] Falscher Schlüssel (z. B. Seelenglut-Kern an der Ruine der Hauptwelt): Hinweis, Schlüssel bleibt.
- [ ] Richtiger Schlüssel per Rechtsklick auf eine Konsole:
  - Alle 8 Konsolen wechseln in den Noten-Modus, mit Partikeln und Klang.
  - Im Chat steht der Hinweis mit der Melodie **1-3-7-6-5-2-4-8** und der Bedienung (Rechtsklick wechselt,
    Linksklick spielt). Dasselbe erhält ein Admin bei `/sift portal unlock`.
  - Der Schlüssel ist weg (im Kreativmodus bleibt er).
  - Ein zweiter Rechtsklick ergibt den Hinweis „schon wach“.
- [ ] Klangnummern:
  - Rechtsklick auf einen Notenblock schaltet den Klang weiter, immer in der Reihenfolge
    **1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 1**.
  - Die Actionbar zeigt danach „Klang N von 8“ mit der neuen Nummer. Schlagen (Linksklick) spielt den
    Klang und zeigt ebenfalls „Klang N von 8“.
  - Redstone am Notenblock spielt den Sift-Klang ohne Anzeige.
  - Ein Notenblock genügt: einstellen, schlagen, weiterschalten, schlagen …; es zählen nur die
    Schläge. Mehrere Notenblöcke mit fest eingestellten Klängen gehen genauso.
- [ ] `/sift portal info` (Admin) listet die 8 Konsolen mit Position und aktueller Klangnummer; nach dem
      Umstellen eines Notenblocks steht dort die neue Nummer.
- [ ] Melodie 1-3-7-6-5-2-4-8 durch Schlagen spielen:
  - Farbstoß über den Notenblöcken.
  - Die Konsolen spielen die Melodie mit Lichtsäulen nach.
  - Dann füllt sich der Rahmen vom Rand zur Mitte (Partikel, Klang), die Nebelfläche erscheint, die
    Öffnung leuchtet.
- [ ] Falsche Töne in der Melodie: nichts passiert. Rückwärts gespielt: Hinweis „Dieses Portal bleibt offen“.
- [ ] Dasselbe im Nether (Seelenglut-Kern) und im End (Leere-Resonator).

**Eigene Konsolen außerhalb der Sift**
- [ ] Klingenden Tiefenschiefer aus `/sift items` in der Hauptwelt setzen (früher gesperrt), zwei direkt
      nebeneinander: Beide behalten ihr Aussehen. Andere Sift-Blöcke bleiben außerhalb der Sift gesperrt.
- [ ] Eigener Rahmen mit Konsolen: `/sift portal create ~ ~ ~`, `unlock`, Melodie wie oben.

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
- [ ] Rezepte im Rezeptbuch: Echo-Resonator, Seelenglut-Kern, Leere-Resonator, Sift-Kompass. Mit
      Vanilla-Zutaten herstellbar; die Items glänzen und haben eigene Texturen. Die Kompassnadel dreht
      sich sauber.
- [ ] Mehrere Truhen in Antiker Stadt, Bastion und Endsiedlung öffnen: Gelegentlich (8 %) liegt der
      passende Schlüssel darin, der normale Inhalt bleibt.

## Melodie je Portal (ab alpha.20)

- [ ] Zwei neu generierte Schreine (oder `/sift portal create` an zwei Rahmen) wecken: Jeder zeigt beim
      Wecken eine andere Melodie (Chat und Actionbar an Spieler in der Nähe). Mit dem Schlüssel und mit dem
      Singer (Ziegenhorn) prüfen.
- [ ] Die gezeigte Melodie öffnet genau diesen Schrein; die feste Melodie 1-3-7-6-5-2-4-8 öffnet ihn nicht.
- [ ] Rückruf: Schleichen + Rechtsklick mit leerer Hand auf einen Notenblock eines geweckten Schreins zeigt
      die Melodie nur dir; der Klang des Notenblocks schaltet dabei nicht weiter. An einem schlafenden oder
      offenen Portal und an der Antiken Stadt passiert nichts Besonderes. `recall-enabled: false` schaltet
      den Rückruf ab.
- [ ] Neustart: `/sift portal info` zeigt dieselben Melodien wie vorher.
- [ ] Ein schon vor alpha.20 geweckter Schrein behält 1-3-7-6-5-2-4-8 (`info`: „feste Melodie“).
- [ ] `portals.melody.per-portal: false` + Neustart: alle Schreine öffnen wieder mit `fixed`.
- [ ] Antike Stadt: weiter nur die lange Melodie aus dem Buch, kein Hinweis beim Wecken.
- [ ] Konsole (`logs/latest.log`): keine Melodie im Log.

## Zugang zur Sift (ab alpha.20)

Zum Testen einen Spieler **ohne** Bypass nehmen (OP hat ihn: `lp user <Spieler> permission set
thesift.access.bypass false`, danach wieder entfernen).

- [ ] `/sift close` während du in der Sift bist: Chat „schließt in 30 Sekunden“, Actionbar zählt herunter,
      danach stehst du an der Stelle, an der du hinein bist (ohne Rückkehrpunkt, z. B. nach `/sift tp`: am
      Spawn der Hauptwelt) mit der Meldung „zurückgeschickt“.
- [ ] `/sift close`, dann vor Ablauf `/sift open`: Countdown bricht ab, niemand wird zurückgeschickt.
- [ ] Geschlossen durch ein offenes Portal gehen: Actionbar „Die Sift ist geschlossen“ (bei after-dragon /
      fixed-date mit Restzeit), man bleibt draußen. Tier hineinführen / Item hineinwerfen: bleibt draußen.
- [ ] Geschlossen: Schrein wecken und Melodie spielen geht weiter, das Portal öffnet sich.
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

## Risse (ab alpha.22)

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
`mobs.singer` und `portals.ancient-city`. Melodie und Konsolen im Überlebensmodus spielen.

**Singer rufen (Schrein)**
- [ ] Ziegenhorn in bis zu 40 Blöcken von einem schlafenden Schrein blasen: Nach etwa 3 Sekunden steigt der
      Singer im Portal aus dem Boden (Partikel, Klang), zur nächsten Konsole gedreht.
- [ ] Er singt, im weiten Umkreis zu hören. Acht Schallwellen (türkise Partikel) laufen nacheinander zu den
      Konsolen; jede Konsole wacht auf, wenn ihre Welle ankommt (Farbstoß).
- [ ] Danach versinkt er wieder im Boden und ist weg. Spieler in der Nähe bekommen den Hinweis mit der Melodie;
      `/sift portal info` zeigt „8/8 wach“, Zustand UNLOCKED.
- [ ] Ein zweites Horn während des Gesangs ruft keinen zweiten Singer. An einem wachen oder offenen Portal
      erscheint keiner.
- [ ] Während des Auftritts ist er nicht zu verletzen und nicht wegzuschieben.
- [ ] Der Schlüssel geht weiterhin. Mit `portals.keys.enabled: false` (Neustart) weist die Konsole den
      Schlüssel ab („Schlüssel sind abgeschaltet …“), der Singer weckt weiter; die Schlüssel liegen dann
      nicht mehr in Truhen.

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
- [ ] Echo-Resonator oder Singer (Ziegenhorn) weckt es. Der Hinweis nennt **keine** Melodie, nur dass es ein
      eigenes Lied gibt.
- [ ] Die Schreinmelodie 1-3-7-6-5-2-4-8 öffnet es nicht. Die lange Melodie aus der config
      (`portals.ancient-city.melody`, Standard 6-2-8-4-1-7-3-5-8-2-6-1) öffnet es; Durchgang führt in die Sift
      und zurück.
- [ ] Truhen der Antiken Stadt: Gelegentlich (15 %) liegt das Buch „Lied der Tiefe“ darin, auf Seite 2
      steht die Melodie.
- [ ] `/sift portal unlock city-…` (Admin) zeigt die Melodie zusätzlich als Admin-Hinweis.
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
