# Texturen-Abdeckung: Original (Mielon's The Sift 1.1.1) → Paper-Umsetzung

Stand: 0.1.0-alpha.4. Der Bericht entstand nach alpha.1 als Entscheidungsgrundlage; Abschnitt 5
hält die Entscheidungen fest, Abschnitt 6 die Korrekturen an der ersten Fassung. Umgesetzt in alpha.3:
Weiden-Vollblöcke und Weidenlaub, Lotus, Ichorglas mit voller Form, Ichorfarbe. In alpha.4 (Schritt 5)
kamen Siftit-Ausrüstung, Siftitbarren, Aufwertungsvorlage, Sifterfleisch, Ichorflasche und die beiden
Schallplatten dazu.

## 1. Zusammenfassung

| | Original | mit Original-Optik im Spiel | nur Vanilla-Ersatz | noch nicht im Spiel |
|---|---|---|---|---|
| Blöcke (Blockstates) | 64 | 39 | 25 (davon Portal = Luft, Kessel = Wasserkessel) | – |
| Item-Definitionen | 90 | 60 | 22 (20 Block-Items der Ersatzblöcke, 2 Boote) | 8 (Ichoreimer, Spawn-Eier, Riss, Portal) |
| Texturen (PNG) | 134 | 77 | 18 (Weiden-Funktionsblöcke, Ranken, Boote, Glasscheibe, Kessel) + 2 Ichor (Farbe per Biom, F2) | 37 (Ichoreimer/Riss, Mobs Phase 2, Partikel/Umgebung/GUI) |

- **Alle 134 Texturen und alle Modelle des Originals liegen im Pack** (nur Shader und GeckoLib-Dateien
  sind ausgelassen). „Nur teilweise übernommen“ heißt: sie liegen im Pack, werden aber von keinem
  Block oder Item im Spiel benutzt.
- Ursachen für Unsichtbares: (a) Vanilla-Ersatzblöcke (Stein-Formblöcke, Weiden-Set, Kleinkram),
  (b) Items späterer Schritte (Ichoreimer, Riss, Spawn-Eier, Portal), (c) Mobs/Partikel/Umgebung (Phase 2/3,
  teils Client-Code).

### Prüfmethode

- Automatisch aus Jar 1.1.1, `pack-src/block-mapping.json` und `pack-src/resourcepack`: für jeden
  Blockstate und jede Item-Definition alle Modelle (inkl. Eltern) und Texturen aufgelöst.
- `PackValidator` (packgen) prüft bei jedem Import, dass jedes referenzierte Modell und jede Textur
  existiert (0 Fehler). `GeneratedDatapackTest` prüft, dass jeder umgedeutete Zustand im Override
  ein Mod-Modell bekommt (39 Blöcke, alle vorhanden). `MappingStabilityTest` prüft, dass alle Zustände
  aus alpha.2 unverändert bleiben (bestehende Welten behalten ihr Aussehen).
- Vanilla-Verhalten der Träger aus dem 26.3-Server gelesen (Form von Chorus und Stolperdraht).

## 2. Gefundene Fehler

| # | Was | Befund | Schwere |
|---|---|---|---|
| | **Stand alpha.3** | F1 behoben (Chorus mit Armen, siehe Abschnitt 5), F2 behoben (Biom-Wasserfarbe `#9dc5ff`), F3 bleibt, F4 kommt mit Phase 3. | |
| F1 | **Ichorglas: Hitbox** | Träger `chorus_plant` ohne Verbindungen. In 26.3 ist die Chorus-Pflanze ein `PipeBlock` mit 10-px-Kern: Kollision und Auswahl sind ein 10×10×10-px-Würfel in der Mitte, man läuft halb hinein. Außerdem blendet Glas-an-Glas die Innenflächen nicht aus (nur echtes Glas kann das), Glaswände zeigen Gitterlinien. | hoch (Verhalten) |
| F2 | **Ichor (Flüssigkeit): Farbe** | Original: eigene, bereits gefärbte Textur `ichor_still/flow` (Mittel RGB 109/137/213, bläulich-lila, ohne Tint). Bei uns: Vanilla-Wasser mit der Biom-Wasserfarbe `#4adce8` aus dem Original-Biom (türkis). Die Ichor-Texturen werden nicht benutzt. | mittel (Optik) |
| F3 | **Pflanzen auf Stolperdraht: Auswahlbox** | Optik korrekt (Original-Kreuzmodelle), aber die Auswahlbox ist die flache Stolperdraht-Form am Boden; bei hängenden Wurzeln sitzt sie am falschen Ende des Blocks. Abbauen geht, das Anvisieren ist ungewohnt. | niedrig (Verhalten) |
| F4 | **Bewachsener Siftschiefer, verschneit** | Variante mit Ichorschnee-Oberseite existiert (eigener Zustand), entsteht aber nur per Weltgenerierung; das Original schaltet sie je nach Ichorschnee darüber. Kommt mit Ichorschnee (Phase 3). | niedrig |

Ohne Befund geprüft:
- **Mapping:** Alle 32 umgedeuteten Blöcke zeigen ihr eigenes Modell; Zufallsvarianten (gespiegelter
  Siftschiefer, gedrehter Bewuchs, Sculk-Rotationen) sind übernommen.
- **Modelle/Texturen:** keine fehlenden Referenzen, keine `tintindex`-Flächen (keine Einfärbung nötig).
  Der Fabric-Schlüssel `render_type` in Pflanzenmodellen wird von Vanilla ignoriert; in 26.3 wählt die
  Textur selbst den Render-Layer (Pflanzen „cutout“, Ichorglas `force_translucent`) – passt.
- **Partikel beim Abbauen:** kommen aus dem Modell des Zustands, also aus dem Original-Modell.
- **Vanilla-Pilzblöcke:** Die 64 Zustände werden mit erzeugten Würfelmodellen nachgebildet
  (außen/innen je Seite). Die Standard-UVs eines Würfels entsprechen den `uvlock`-Teilen des
  Vanilla-Multiparts; eine Abweichung ist nicht zu erwarten, die Sichtprüfung steht in `docs/TESTING.md`.
- **Rüstungs-Optik:** `equipment/siftite.json` und die beiden Rüstungstexturen sind im Pack; die
  Siftit-Rüstung nutzt sie über `equippable(asset_id=the_sift:siftite)` (seit alpha.4).

## 3. Bestandsliste

### A. Blöcke mit Original-Optik (39)

| Block | Name | Vanilla-Träger | Texturen |
|---|---|---|---|
| `chiseled_siftslate` | Gemeißelter Siftschiefer | `mushroom_stem` | 1 |
| `cobbled_siftslate` | Siftschiefer-Bruchstein | `mushroom_stem` | 1 |
| `dry_healthy_sculk` | Trockener gesunder Sculk | `red_mushroom_block` | 1 |
| `dry_healthy_sculk_growth` | Bewachsener trockener Sculk | `red_mushroom_block` | 3 |
| `dry_healthy_sculk_sprouts` | Trockene Sculksprossen | `tripwire` | 1 |
| `healthy_sculk` | Gesunder Sculk | `brown_mushroom_block` | 1 |
| `healthy_sculk_sprouts` | Gesunde Sculksprossen | `tripwire` | 1 |
| `ichor_glass` | Ichorglas | `chorus_plant` (oben + 4 Seiten, unten nicht) | 1 |
| `ichor_snow_block` | Ichorschneeblock | `mushroom_stem` | 1 |
| `overgrown_chard` | Überwucherter Mangold | `tripwire` | 1 |
| `overgrown_fronds` | Überwucherte Wedel | `tripwire` | 1 |
| `overgrown_hanging_roots` | Hängende überwucherte Wurzeln | `tripwire` | 1 |
| `overgrown_lotus` | Überwucherter Lotus | `tripwire` | 2 |
| `overgrown_stalks` | Überwucherte Halme | `tripwire` | 1 |
| `overgrown_willow_foliage` | Überwuchertes Weidenlaub | `brown_mushroom_block` | 1 |
| `overgrown_willow_log` | Überwucherter Weidenstamm | `brown_mushroom_block` (je Achse) | 2 |
| `overgrown_willow_planks` | Überwucherte Weidenbretter | `brown_mushroom_block` | 1 |
| `overgrown_willow_sapling` | Überwucherter Weidensetzling | `tripwire` | 1 |
| `overgrown_willow_wood` | Überwuchertes Weidenholz | `brown_mushroom_block` (je Achse) | 1 |
| `reinforced_siftslate` | Verstärkter Siftschiefer | `mushroom_stem` | 3 |
| `sculkflower` | Sculkblume | `tripwire` | 1 |
| `sculkflower_crop` | Sculkblumenpflanze | `tripwire` | 3 |
| `sift_rubble` | Sift-Geröll | `mushroom_stem` | 1 |
| `siftslate` | Siftschiefer | `mushroom_stem` | 2 |
| `siftslate_bricks` | Siftschieferziegel | `mushroom_stem` | 1 |
| `siftslate_charoite_ore` | Siftschiefer-Charoiterz | `mushroom_stem` | 1 |
| `siftslate_coal_ore` | Siftschiefer-Steinkohle | `mushroom_stem` | 1 |
| `siftslate_diamond_ore` | Siftschiefer-Diamanterz | `mushroom_stem` | 1 |
| `siftslate_emerald_ore` | Siftschiefer-Smaragderz | `mushroom_stem` | 1 |
| `siftslate_growth` | Bewachsener Siftschiefer | `mushroom_stem` | 6 |
| `siftslate_hanging_roots` | Hängende Siftschieferwurzeln | `tripwire` | 1 |
| `siftslate_siftite_ore` | Siftschiefer-Siftiterz | `mushroom_stem` | 1 |
| `siftslate_stalks` | Siftschieferhalme | `tripwire` | 1 |
| `sonorous_deepslate` | Klingender Tiefenschiefer | `mushroom_stem` | 4 |
| `soul_block` | Seelenblock | `mushroom_stem` | 1 |
| `stripped_overgrown_willow_log` | Entrindeter überwucherter Weidenstamm | `brown_mushroom_block` (je Achse) | 2 |
| `stripped_overgrown_willow_wood` | Entrindetes überwuchertes Weidenholz | `brown_mushroom_block` (je Achse) | 1 |
| `sunburst_plant` | Sonnenstrahlpflanze | `tripwire` | 3 |
| `whisperbloom` | Flüsterblüte | `tripwire` | 1 |

### B. Blöcke nur als Vanilla-Ersatz (25)

| Block | Name | Ersatz | Original-Texturen ungenutzt |
|---|---|---|---|
| `cobbled_siftslate_slab` | Siftschiefer-Bruchsteinstufe | `cobbled_deepslate_slab` | 1 |
| `cobbled_siftslate_stairs` | Siftschiefer-Bruchsteintreppe | `cobbled_deepslate_stairs` | 1 |
| `cobbled_siftslate_wall` | Siftschiefer-Bruchsteinmauer | `cobbled_deepslate_wall` | 1 |
| `ichor_cauldron` | Ichorkessel | `water_cauldron` | 1 |
| `ichor_glass_pane` | Ichorglasscheibe | `purple_stained_glass_pane` | 2 |
| `ichor_snow` | Ichorschnee | `snow` | 1 |
| `overgrown_willow_button` | Überwucherter Weidenknopf | `mangrove_button` | 1 |
| `overgrown_willow_door` | Überwucherte Weidentür | `mangrove_door` | 2 |
| `overgrown_willow_fence` | Überwucherter Weidenzaun | `mangrove_fence` | 1 |
| `overgrown_willow_fence_gate` | Überwuchertes Weidenzauntor | `mangrove_fence_gate` | 1 |
| `overgrown_willow_hanging_sign` | Überwuchertes Weidenhängeschild | `mangrove_hanging_sign` | 2 |
| `overgrown_willow_pressure_plate` | Überwucherte Weidendruckplatte | `mangrove_pressure_plate` | 1 |
| `overgrown_willow_shelf` | Überwuchertes Weidenregal | `mangrove_shelf` | 2 |
| `overgrown_willow_sign` | Überwuchertes Weidenschild | `mangrove_sign` | 2 |
| `overgrown_willow_slab` | Überwucherte Weidenstufe | `mangrove_slab` | 1 |
| `overgrown_willow_stairs` | Überwucherte Weidentreppe | `mangrove_stairs` | 1 |
| `overgrown_willow_trapdoor` | Überwucherte Weidenfalltür | `mangrove_trapdoor` | 1 |
| `overgrown_willow_vines` | Überwucherte Weidenranken | `vine` | 2 |
| `overgrown_willow_vines_plant` | Überwucherte Weidenranken | `vine` | 2 |
| `overgrown_willow_wall_hanging_sign` | Überwuchertes Weidenwandhängeschild | `mangrove_wall_hanging_sign` | 2 |
| `overgrown_willow_wall_sign` | Überwuchertes Weidenwandschild | `mangrove_wall_sign` | 2 |
| `sift_portal` | Sift-Portal | `air` | 1 |
| `siftslate_brick_slab` | Siftschieferziegelstufe | `deepslate_brick_slab` | 1 |
| `siftslate_brick_stairs` | Siftschieferziegeltreppe | `deepslate_brick_stairs` | 1 |
| `siftslate_brick_wall` | Siftschieferziegelmauer | `deepslate_brick_wall` | 1 |

### C. Items mit Original-Optik im Spiel (60 von 90)

`charoite`, `chiseled_siftslate`, `cobbled_siftslate`, `cooked_sifter_meat`, `dry_healthy_sculk`, `dry_healthy_sculk_growth`, `dry_healthy_sculk_sprouts`, `healthy_sculk`, `healthy_sculk_sprouts`, `ichor_bottle`, `ichor_glass`, `ichor_shard`, `ichor_snow_block`, `ichor_snowball`, `music_disc_rift`, `music_disc_wellspring`, `overgrown_chard`, `overgrown_fronds`, `overgrown_hanging_roots`, `overgrown_lotus`, `overgrown_stalks`, `overgrown_willow_foliage`, `overgrown_willow_log`, `overgrown_willow_planks`, `overgrown_willow_sapling`, `overgrown_willow_wood`, `raw_sifter_meat`, `reinforced_siftslate`, `sculkflower`, `sculkflower_seeds`, `sift_rubble`, `siftite_axe`, `siftite_boots`, `siftite_chestplate`, `siftite_helmet`, `siftite_hoe`, `siftite_ingot`, `siftite_leggings`, `siftite_nugget`, `siftite_pickaxe`, `siftite_shovel`, `siftite_spear`, `siftite_sword`, `siftite_upgrade_smithing_template`, `siftslate`, `siftslate_bricks`, `siftslate_charoite_ore`, `siftslate_coal_ore`, `siftslate_diamond_ore`, `siftslate_emerald_ore`, `siftslate_growth`, `siftslate_hanging_roots`, `siftslate_siftite_ore`, `siftslate_stalks`, `sonorous_deepslate`, `soul_block`, `stripped_overgrown_willow_log`, `stripped_overgrown_willow_wood`, `sunburst_plant`, `whisperbloom`

### D. Items, die (noch) nicht als Sift-Item existieren (30)

| Gruppe | Items | Grund | Wann |
|---|---|---|---|
| Block-Items der Ersatzblöcke (20) | Bruchstein- und Ziegel-Treppe, -Stufe, -Mauer (6); Weiden-Ranken, -Treppe, -Stufe, -Zaun, -Zauntor, -Tür, -Falltür, -Knopf, -Druckplatte, -Schild, -Hängeschild, -Regal (12); Ichorschnee, Ichorglasscheibe (2) | Block ist Vanilla-Ersatz, es gibt das Vanilla-Item (z. B. Mangroven-Tür) | bleibt so (Abschnitt 5) |
| Boote (2) | Weidenboot, Weiden-Truhenboot | Boote sind Entities mit fester Optik; Ersatz Mangrovenboot | nur per Display-Aufbau möglich |
| Ichor (2) | Ichoreimer, Sift-Riss | Modelle/Texturen im Pack, Item noch nicht registriert | Phase 3 |
| Spawn-Eier (5) | Sänger, Echogolem, Dunkler Schnüffler, Blub, Sifter | Mobs fehlen noch | Phase 2 |
| Portal-Item (1) | Sift-Portal | Portal kommt als Display-Aufbau | Schritt 7 |

### E. Texturen ohne sichtbare Verwendung (57)

| Gruppe | Anzahl | Grund |
|---|---|---|
| Weiden-Set: Blocktexturen (Ranken, Tür, Falltür, Schild, Regal) + Item-Texturen Tür/Schild/Hängeschild | 8 + 3 | Vanilla-Ersatz Mangrove/Ranke |
| Ichorglasscheibe, Ichorkessel | 2 | Vanilla-Ersatz |
| Ichor still/flow | 2 | Wasser mit Biomfarbe im Ichor-Ton (F2) |
| Sift-Portal-Nebel, Klingender-Tiefenschiefer-Strahl | 2 | Portal/Strahl kommen mit Schritt 7 / Phase 2 |
| Item-Texturen: Ichoreimer, Riss (2), Spawn-Eier (5), Boote (2) | 9 | Phase 3 / Phase 2 / Boote Ersatz |
| Mob-Texturen inkl. Leuchtmasken und Riss | 12 | Phase 2 |
| Boot-Entity-Texturen, Hängeschild-GUI | 3 | Vanilla-Ersatz |
| Partikel (Ichor, Note, Seele, Schallwelle, Portalnebel) | 9 | eigene Partikeltypen brauchen Client-Code; ggf. als Display-Partikel nachbaubar (Phase 3) |
| Umgebung (Ichor-Regen/-Schnee), HUD (Ichor-Luftblasen), Slot-Icon Charoit, Shaderpack-Bilder | 7 | wirken global oder brauchen Client-Code |

## 4. Wege zu eigener Optik und Empfehlung je Gruppe

### Bausteine (gelten für mehrere Gruppen)

| Weg | Wie | Aufwand | Performance | Verhalten / Risiken |
|---|---|---|---|---|
| **W1 Freie Pilzblock-Zustände** | weitere `down=false`-Zustände (frei: Stiel 14, brauner 31, roter 30 = 75) | klein (Mapping + Config) | keine Kosten | nur **voller, undurchsichtiger Würfel**; Holzklang; brennt nicht, kein Brennstoff, nicht in Holz-Tags (Rezepte/Entrinden per Plugin); Achsen-Varianten brauchen je 3 Zustände |
| **W2 Stolperdraht-Zustände** | frei: 17 | klein | keine | **keine Kollision**, flache Auswahlbox (F3), nicht kletterbar; darf über Wasser schweben |
| **W3 Chorus-Zustände** | frei: 31 (`down=false`) | klein | keine | verdeckt keine Nachbarn (gut für Transparenz), aber **Kreuzform-Kollision** (10-px-Kern + Arme), keine volle Fläche |
| **W4 Blätter-Zustände** | z. B. `azalea_leaves` (ungefärbt, ohne eigene Partikel; Kirschlaub fällt aus, es streut Blüten) mit `persistent=true, waterlogged=true` (je 7 Zustände; nur erreichbar, wenn jemand diese Blätter unter Wasser setzt) | mittel (PhysicsGuard + Brand verhindern) | keine | **voller Würfel, durchscheinend**; Blätterklang; brennbar (für Glas per Plugin unterdrücken); in „Schnell“-Grafik rendert der Client Blätter undurchsichtig → muss getestet werden |
| **W5 Notenblock-Zustände** | ~1.150 Zustände, aber alle im Vanilla-Spiel erreichbar | groß | keine | Plugin müsste **alle** Notenblöcke übernehmen; kollidiert mit der Ancient-City-Melodie (Phase 2). Nicht empfohlen |
| **W6 Unerreichbares Vanilla-Duplikat umtexturieren** | nur `petrified_oak_slab` (im Survival nicht erhältlich, Stein-Verhalten) | klein | keine | genau **eine** Stufe; für Treppen/Mauern gibt es kein solches Duplikat |
| **W7 Vanilla-Block global umtexturieren** | z. B. Mangrove → Weide | klein | keine | ändert den Block **überall** (Mangrovensümpfe der Overworld). Nur mit ausdrücklichem Ja |
| **W8 Display-Entity über Träger** | `ItemDisplay` mit Original-Modell, 0,2 % größer, über einem Vanilla-Block mit gleicher Form (z. B. Tiefenschiefer-Treppe); Display folgt Platzieren, Drehen, Abbau, Explosion, Kolben | groß | 1 Entity pro Block: unkritisch bei Dutzenden, ab einigen Hundert in Sicht spürbar (Client-FPS, Chunk-Laden); keine Tick-Last | volle Vanilla-Form und -Verhalten; Risse beim Abbauen und Partikel kommen vom Träger; Licht etwas weicher; Kolben/Strukturen müssen nachgezogen werden |
| **W9 Vanilla-Ersatz behalten** | wie jetzt | – | – | falsche Optik, sonst volles Vanilla-Verhalten |

### Empfehlung je Gruppe

Stand der ersten Fassung. Was entschieden und umgesetzt wurde, steht in Abschnitt 5.

| Gruppe | Blöcke | Empfehlung | Begründung |
|---|---|---|---|
| **G1 Stein-Formblöcke** | Bruchstein-/Ziegel-Treppe, -Stufe, -Mauer (6) | **W9 behalten**; optional **W6** für eine Stufe | Treppen/Stufen/Mauern werden in großen Mengen verbaut → W8 zu teuer; Tiefenschiefer passt im Farbton schlecht, ist aber verhaltensgleich. Falls dir die Optik wichtig ist: W8 nur für Ziegel-Varianten (Dekor, kleinere Mengen) |
| **G2 Weiden-Vollblöcke** | Stamm, Holz, entrindet (je 3 Achsen), Bretter (13 Zustände) | **W1** | Prägen das Bild der Sift-Wälder (Bäume ab Schritt 6). Unterschiede (brennt nicht, Entrinden/Rezepte per Plugin) sind verkraftbar |
| **G3 Weidenlaub** | Laub | **W4** (`azalea_leaves`, ungefärbt) | voller Würfel und durchscheinend wie Laub; brennbar ist hier richtig; Test in „Schnell“-Grafik nötig. Rückfall: W1 (undurchsichtig) |
| **G4 Weidenranken** | Ranke, Rankenpflanze | **W9 behalten** (Vanilla-Ranke) | Original leitet von `VineBlock` ab und steht im Tag `climbable`; die Vanilla-Ranke verhält sich gleich. W2 sähe richtig aus, wäre aber nicht kletterbar |
| **G5 Weiden-Funktionsblöcke** | Tür, Falltür, Zaun, Zauntor, Knopf, Druckplatte, Schilder, Regal, Treppe, Stufe, Boote | **W9 behalten** (Mangrove) | viele Zustände, Block-Entities, Bewegung; nur W8 wäre möglich und lohnt den Aufwand nicht. Mangrove ist rötlich und passt zur Sift-Palette |
| **G6a Lotus** | Lotus | **W2** | Original (`OvergrownLotusBlock`) hat keine Kollision, nur eine 4 px hohe Auswahlbox – W2 passt im Verhalten fast genau (Seerosen-Ersatz ist dagegen begehbar) |
| **G6b Ichorschnee** | Schneeschichten (8 Höhen) | **W9 behalten** bis Phase 3, dann **W8** nur für die Oberfläche der Ichorschnee-Gipfel | Schichthöhe braucht echte Kollision |
| **G6c Ichorglasscheibe** | Scheibe | **W9 behalten** (lila Glasscheibe) | Scheiben-Verbindungslogik nur mit W8 |
| **G6d Ichorkessel** | Kessel | **W9 behalten** | Inhalt wird mit F2 ohnehin ichorfarben |
| **F1 Ichorglas** | Ichorglas | **auf W4 umziehen** (Blätter-Zustand, Brand unterdrücken); Rückfall W3 mit allen Armen außer unten | volle Kollision; Chorus bleibt Kreuzform |
| **F2 Ichor** | Flüssigkeit | **Biom-Wasserfarbe im Datapack auf Ichor-Ton** (≈ `#6d89d5`) | eine Zeile pro Biom, sofort sichtbar; Ichor-Textur selbst bleibt Client-Sache |
| **F3 Pflanzen-Auswahlbox** | Pflanzen, Wurzeln | **so lassen** | rein kosmetisch beim Anvisieren; Alternativen (W8) zu teuer |
| **Portal, Strahl** | Sift-Portal, Klingender Strahl | **W8** (wie im Plan, Schritt 7 / Phase 2) | ohnehin Display-basiert geplant |

## 5. Entscheidungen (umgesetzt in alpha.3)

| # | Punkt | Entscheidung | Umsetzung |
|---|---|---|---|
| 1 | G2 Weiden-Vollblöcke | **W1** | 14 Zustände des braunen Pilzblocks: Stamm, Holz, entrindeter Stamm, entrindetes Holz je Achse x/y/z, Bretter, Laub. Achse nach angeklickter Fläche, Entrinden per Rechtsklick mit Axt (Achse bleibt, Axt verliert 1 Haltbarkeit). Härte 2,0 mit Axt. |
| 2 | G3 Weidenlaub | **W1** (statt W4) | Die Original-Textur ist vollständig deckend (256 von 256 Pixeln), Blätter-Zustände bringen also nichts. Unterschiede zum Original: zerfällt nicht, brennt nicht, Holzklang. Härte 0,7, mit Hacke schneller. |
| 2 | F1 Ichorglas | **W3 „Chorus mit Armen“** (statt W4) | `chorus_plant[up,north,east,south,west=true, down=false]`: Kern plus fünf Arme, 61 % des Würfels als Kollision (vorher 24 %). Der alte Zustand (alle Arme aus) wird weiter als Ichorglas erkannt und angezeigt (Migration, siehe `docs/ADMIN.md`). |
| 3 | F2 Ichorfarbe | **Biom-Wasserfarbe** | `water_color` `#9dc5ff` in allen 10 Sift-Biomen (gilt auch für Kessel). Herleitung in `packgen/src/main/resources/worldgen-overrides.yml`. |
| 4 | G6a Lotus | **W2** | Stolperdraht-Zustand, Pflanzenregel wie die übrigen Sift-Pflanzen, droppt sich selbst. |
| 5 | G1 Stein-Formblöcke | **W9 behalten** | Tiefenschiefer-Varianten. |
| 6 | G4 Ranken | **W9 behalten** | Vanilla-Ranke, kletterbar. |
| 7 | W7 | **ausgeschlossen** | Kein Vanilla-Block wird global umtexturiert. |

F3 (Auswahlbox der Pflanzen) bleibt wie beschrieben; F4 kommt mit Phase 3.

Freie Zustände nach alpha.3: Stiel 14, brauner Pilzblock 17, roter 30, Stolperdraht 16, Chorus 30.

## 6. Korrekturen an der ersten Fassung

- **Lotus:** Im Original eine Bodenpflanze (`SiftPlantBlock`, wächst auf Sift-Boden), keine
  Wasserpflanze. Der Seerosen-Ersatz war also auch optisch falsch; die Pflanzenregel ist die normale.
- **Weidenlaub mit W4:** unnötig, die Textur hat keine durchsichtigen Pixel (siehe oben).
- **W4 `azalea_leaves` für Ichorglas:** nicht nutzbar. Mit `waterlogged=true` zeigt der Client Wasser
  im Block, und alle anderen Zustände entstehen an natürlichen Azaleen oder beim Setzen.
- **Ichorfarbe:** Der Vorschlag `#6d89d5` war der Ton der Textur selbst. Vanilla-Wasser ist grau (Mittel
  177/255) und wird mit der Biomfarbe multipliziert; für 109/137/213 ergibt sich `#9dc5ff`.
- **Chorus-Zustand des Ichorglases im End:** Ein Chorus mit `down=false` überlebt in Vanilla nur, wenn
  ein seitlicher Chorus-Nachbar getragen ist. Der gewählte Zustand braucht vier Chorus-Nachbarn
  seitlich und einen darüber, ohne Stütze darunter. Natürlicher Wuchs erzeugt das nicht (eine
  Chorusblüte wächst nur in Richtungen, deren Nachbarn leer sind). Gezielt gebaut ließe er sich im End
  mit Chorusblüten herstellen; der Block sähe dann wie Ichorglas aus, bliebe aber Chorus.
  Smoke-Test: in 3014 natürlich generierten Chorus-Pflanzen des Ends kam kein von der Sift genutzter
  Zustand vor (`/sift debug chorus`).
