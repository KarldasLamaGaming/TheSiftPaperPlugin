# The Sift – Test-Checkliste

Für den separaten Testserver, nie auf dem Produktivserver. Paper 26.3, frische Welt, Vanilla-Client 26.3.
Jeder Punkt: Erwartung in Klammern. Abweichungen bitte mit Screenshot (F3 sichtbar) und Log-Auszug melden.

## Vorbereitung

- [ ] Plugin-Jar und `resource-pack.url` aus demselben Release (Standard passt automatisch).
- [ ] Log beim Start: `Datapack installed`, `Found new data pack TheSift/the_sift`,
      `The Sift is loaded as world …`, `Resource pack … (SHA-1 …)`; keine `ERROR`-Zeilen von TheSift.
- [ ] `/datapack list` (TheSift/the_sift aktiv), `/sift pack info` (URL und SHA-1 wie
      `the-sift-pack.zip.sha1` im Release).
- [ ] `config.yml`: `debug.populator-marker: false` (keine Glowstone-Blöcke im 16er-Raster in der Sift).

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

## Abbau

- [ ] Siftschiefer: Diamantspitzhacke ≈ 0,6 s, von Hand ≈ 15 s ohne Drop, Axt langsam wie von Hand.
- [ ] Siftiterz: mit Eisenspitzhacke langsam und ohne Drop, mit Diamantspitzhacke ≈ 0,85 s.
- [ ] Verstärkter Siftschiefer: nicht abbaubar (Risse erscheinen nicht).
- [ ] Gegenprobe: `/sift debug break <x> <y> <z> diamond_pickaxe` zeigt dieselben Zeiten.

## Drops

- [ ] Siftschiefer → Siftschiefer-Bruchstein; mit Behutsamkeit → Siftschiefer.
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
- [ ] Werkzeuge/Rüstung: „Noch keine Items in dieser Kategorie“ (kommen mit Schritt 5).
- [ ] `/sift give <spieler> <id> [anzahl]`: Tab-Vervollständigung zeigt alle Ids.
- [ ] Spieler nur mit `thesift.admin.items`: `/sift items` und `/sift give` gehen, `/sift tp`,
      `/sift pack`, `/sift debug` nicht sichtbar. Spieler ohne Rechte: `/sift` unbekannt.

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
