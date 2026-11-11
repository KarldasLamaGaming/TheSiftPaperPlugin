# Go-live-Checkliste

Reihenfolge für den Produktivstart. Jeder Schritt hat kopierbare Befehle und das erwartete Ergebnis.
Spieltests nur auf dem **Testserver**, Reset und Config-Abgleich auf dem **Produktivserver** im Wartungsfenster.
Befehle ohne `/` sind für die Konsole, Befehle mit `/` für einen Spieler im Spiel.

Platzhalter: `<spieler>` = Spielername, `<welt>` = `level-name` aus `server.properties` (meist `world`),
`N` = laufende Nummer des Backups (z. B. Datum `20261106`).

---

## 1. Spieltests (Testserver)

Ergebnis jeweils als Kommentar ins Issue (Screenshot mit F3, Log-Auszug), dann das Issue schließen.

### #11 Weidenlaub-Härte

Erwartet ist die Härte 0,7 wie im Original (nicht 0,2 wie Vanilla-Laub).

```
/sift tp
/sift give @s overgrown_willow_foliage 8
```
- [ ] 4 Blöcke Weidenlaub setzen. Abbauen von Hand dauert etwa 1 s, mit Eisenhacke kurz (4 Ticks), mit Schere fast
      sofort (etwa 2 Ticks). Es droppt sich selbst.
- [ ] Gegenprobe ohne Client, mit den Koordinaten eines gesetzten Laubblocks (`F3` → „Targeted Block“):
  ```
  /sift debug break <x> <y> <z> air
  /sift debug break <x> <y> <z> iron_hoe
  /sift debug break <x> <y> <z> shears
  ```
  Erwartet: von Hand etwa 21 Ticks, Eisenhacke 4 Ticks, Schere etwa 2 Ticks.

### #12 Rechte ohne OP (`thesift.admin.items`)

Mit einem Test-Account ohne OP (`deop <spieler>`), Rechte über LuckPerms:
```
lp user <spieler> permission set thesift.admin.items true
```
- [ ] `/sift items` öffnet das Menü, `/sift give <spieler> siftite_ingot 2` geht, die Tab-Vervollständigung zeigt nur
      `items` und `give`.
- [ ] `/sift tp`, `/sift pack`, `/sift debug`, `/sift reload` sind weder in der Tab-Liste noch ausführbar.
- [ ] Im Menü lässt sich ein Item nehmen, aber keines hineinlegen.
```
lp user <spieler> permission unset thesift.admin.items
lp user <spieler> permission set thesift.admin true
```
- [ ] Jetzt gehen alle `/sift`-Befehle.
```
lp user <spieler> permission unset thesift.admin
```
- [ ] Ohne Rechte ist `/sift` unbekannt.

Für eine Admin-Gruppe dauerhaft: `lp group admin permission set thesift.admin true`.

### #13 Amboss, Schleifstein, Händler, Schallplatten, Entrinden, Ichorglas, Feuer

```
/gamemode survival
/sift give @s siftite_sword 2
/sift give @s siftite_ingot 4
/give @s netherite_sword
/give @s netherite_ingot
/give @s anvil
/give @s grindstone
/give @s jukebox
/give @s experience_bottle 64
/sift give @s music_disc_rift
/sift give @s music_disc_wellspring
/sift give @s charoite 8
```
- [ ] **Amboss:** jeweils kein Ergebnis bei Siftitschwert + Netheritbarren, Netheritschwert + Siftitschwert,
      Netheritschwert + Siftitbarren. Möglich sind: Reparatur mit Siftitbarren, zwei Siftitschwerter kombinieren,
      ein Buch auftragen, Umbenennen.
- [ ] **Schleifstein:** Siftitschwert + Netheritschwert ergibt nichts; ein Siftitschwert allein (entzaubern) geht.
- [ ] **Händler:** `/summon villager ~ ~ ~ {VillagerData:{profession:"minecraft:librarian",level:2}}`: Charoit und
      Siftitbarren werden nicht als Papier angenommen, Sift-Blöcke werden nicht gehandelt.
- [ ] **Schallplatten:** Rift und Wellspring im Plattenspieler spielen mit Titelanzeige; ein Komparator daneben gibt
      ein Signal.
- [ ] **Entrinden:** in der Sift `/sift give @s overgrown_willow_log 4`, `/give @s iron_axe`, Stamm liegend und
      stehend setzen, Rechtsklick mit der Axt: entrindet, Achse bleibt, Geräusch, Haltbarkeit −1.
- [ ] **Ichorglas „Schnell“:** `/sift give @s ichor_glass 16`, in Optionen → Grafik auf „Schnell“: durchsichtig,
      Glas an Glas ohne innere Flächen, man kann nicht hindurchlaufen. Dasselbe mit „Schön“.
- [ ] **Feuer:** an Weidenstamm, -laub und -brettern `/give @s flint_and_steel`: Feuer breitet sich aus und frisst die
      Blöcke. Danach `/gamerule minecraft:fire_spread_radius_around_player 0`: Feuer breitet sich nicht mehr aus;
      zurück mit dem vorherigen Wert.

### #20 Portal mit dem Schlüssel öffnen (Überlebensmodus, seit alpha.37)

Seit alpha.37 gibt es keine Melodie mehr; der passende Schlüssel öffnet das Portal. Der Test ersetzt den früheren
Melodie-Test von #20.

In der Hauptwelt einen Rahmen bauen (7 breit × 8 hoch aus Verstärktem Tiefenschiefer, Öffnung 5 × 6) und davor
8 Konsolen (Klingender Tiefenschiefer mit Notenblock darauf), siehe TESTING → Portale. Oder einen Schrein über
`/sift portal locate` nehmen.
```
/sift give @s sonorous_deepslate 8
/give @s note_block 8
/give @s reinforced_deepslate 26
/sift portal create ~ ~ ~
/sift portal list
/give @s heavy_core
/give @s echo_shard 4
/give @s sculk_catalyst
/sift give @s soul_ember_core
```
- [ ] Zwei Konsolen nebeneinander behalten ihr Aussehen.
- [ ] Schlafende Konsole: Rechtsklick und Schlagen geben nur den verzerrten Klang und einen Hinweis.
- [ ] Falscher Schlüssel (Seelenglut-Kern): verzerrter Klang, „passt nicht“, Schlüssel bleibt.
- [ ] Echo-Resonator herstellen (Rezeptbuch: Schwerer Kern, 4 Echo-Splitter, Sculk-Katalysator) und per Rechtsklick
      auf eine Konsole: Die Konsolen erwachen nacheinander mit Farbstoß, Auslöse-Klang, die Konsolen spielen mit
      Lichtsäulen nach, der Rahmen füllt sich vom Rand zur Mitte, Nebelfläche erscheint. Der Schlüssel ist weg.
- [ ] Noch ein Rechtsklick mit Schlüssel: „schon offen“, nichts verbraucht.
- [ ] `/sift portal lock <id>`: Fläche zu, Konsolen schlafen; wieder öffnen nur mit einem neuen Schlüssel.
- [ ] Redstone am Notenblock einer geweckten Konsole: Sift-Klang statt Vanilla-Note.
- [ ] Ziegenhorn in der Nähe eines schlafenden Portals: Der Singer erscheint und singt, die Konsolen leuchten auf,
      bleiben aber schlafend.

### #25 Blub (in der Sift, Überlebensmodus)

```
/sift tp
/sift spawn blub
/sift give @s soul_block 16
/sift spawn blub 4
```
Prüfpunkte wie im Issue: Folgen mit Seelenblock, Zähmen (etwa jedes dritte Mal Herzen, Advancement „Blub“), kein
Zähmen während Rudelzorn, Sitzen per Rechtsklick nur durch den Besitzer, Heilen, Verteidigung, Rudelzorn bei 4 Blubs
(16 Blöcke, etwa 30 s), keine Wolfsklänge, Knochen und Fleisch wirkungslos, keine Schafjagd, Leine, Boot und Lore
gehen nicht, Todesnachricht nennt „Blub“. Kontrolle: `/sift debug mobs`.

### #30 Modellteile beim Chunk-Entladen (inkl. Log-Check)

```
/sift tp
/sift spawn blub 2
/sift spawn sifter 2
/execute if entity @e[type=item_display,distance=..30]
```
- [ ] Erwartet: 2 × 7 + 2 × 10 = 34 Displays (Blub 7, Sifter 10 je Mob).
- [ ] Mindestens 300 Blöcke wegfliegen (`/tp @s ~400 ~ ~`), 30 s warten, zurück (`/tp @s ~-400 ~ ~`) und erneut
      `/execute if entity @e[type=item_display,distance=..30]`. Erwartet: wieder 34, keine doppelten Modelle.
- [ ] Dasselbe nach einem Neustart (`stop`, starten, hinfliegen).
- [ ] `/kill @e[type=wolf,distance=..30]` (Blub-Basis): die Modellteile verschwinden sofort mit.
- [ ] `/sift debug mobs` zeigt „verwaiste entfernt“ 0 (die Welt lief nur mit alpha.17 oder neuer).
- [ ] **Log-Check** in der Konsole bzw. `logs/latest.log`:
  ```
  grep -c "prevented from being removed" logs/latest.log
  grep -c "already exists" logs/latest.log
  ```
  Erwartet: beide 0. Danach #30 schließen.

**Zur Warnung „Entity uuid already exists … Sniffer“:**
- Das Plugin vergibt nie selbst UUIDs und schreibt keine Entity-Daten. Schnüffler-Familien entstehen mit `world.spawn`
  (immer neue UUID), die Infektion erzeugt einen neuen Dunklen Schnüffler und entfernt den Schnüffler.
- Gegengeprüft: zwei saubere Neustarts mit 523 geladenen Schnüfflern (darunter die Höhlenfamilien) ohne diese Warnung.
- Die Meldung kommt von Paper, wenn dieselbe Entity in zwei Chunks gespeichert ist. Paper behält die erste und
  verwirft die zweite; im Spiel gibt es keinen doppelten Schnüffler.
- Typische Ursachen: Server nicht sauber beendet (Absturz, `kill`), nachdem der Schnüffler in einen anderen Chunk
  gelaufen war, oder zurückkopierte bzw. teilweise gelöschte Region- oder Entity-Dateien (z. B. bei Tests).
- Wenn sie nach sauberem `stop` erneut erscheint: Koordinaten und Log-Auszug in ein Issue.

---

## 2. Welt-Reset der Sift (Produktivserver, nichts löschen)

Nur die Sift wird neu erzeugt; Hauptwelt, Nether, End und `playerdata` bleiben. Der alte Stand wird **umbenannt**, nicht
gelöscht.

1. Ankündigen und alle Spieler aus der Sift holen. Im Spiel als Admin pro Spieler in der Sift:
   ```
   /execute in the_sift:the_sift as @a[distance=0..] run sift tp
   ```
   Oder die Sift schließen (alle ohne Bypass werden nach dem Countdown hinausgeschickt): `sift close`
2. Server stoppen: `stop`
3. Backup und Umbenennen (Shell im Serverordner):
   ```bash
   mkdir -p ../sift-backups
   cp -a <welt>/dimensions/the_sift/the_sift ../sift-backups/the_sift.preN
   mv <welt>/dimensions/the_sift/the_sift <welt>/dimensions/the_sift/the_sift.preN
   ```
   Paper lädt nur den Ordner `the_sift`; `the_sift.preN` daneben wird ignoriert. Wer den Ordner nicht in der Welt
   lassen will, verschiebt ihn nur nach `../sift-backups/`.
4. Server starten. Erwartet im Log:
   ```
   Selecting a location for the main Sift portal
   Main Sift portal ready at <x> <y> <z>
   ```
   Danach `sift debug portals`: leer bis auf neue Einträge. `sift debug landmarks`: 0 Monolithen, Höhlen usw.
   Tempel (ab alpha.41a, Bastion ab alpha.41.1, der Reset muss **danach** kommen): im Log `Temples: 10 sites on the ring 1500-3000 around 0/0`;
   `sift temple sites` zeigt alle 10 als „noch nicht generiert“, keiner rot. Das Tempel-Register liegt in der Sift und
   ist damit ebenfalls neu.
5. Wieder öffnen, falls geschlossen: `sift access reset` (zurück zum Modus aus der config) oder `sift open`.

**Was mit den Spielerdaten passiert (ohne weiteres Zutun):**

| Daten | Wo | Nach dem Welt-Reset |
|---|---|---|
| Siftit-Sicherungen (Tod) | Spieler, `the_sift:siftite_pending` | bleiben und werden beim nächsten Respawn geliefert |
| Intro-Klang gehört | Spieler, `the_sift:intro_played` | bleibt, das Intro spielt nicht erneut |
| Rückkehrpunkt aus der Sift | Spieler, `the_sift:portal_return` | bleibt (zeigt in die Hauptwelt, unkritisch) |
| Sift-Advancements | `<welt>/advancements` | bleiben |
| Sift-Gegenstände | Inventar, Endertruhe, Kisten | bleiben |
| Portal-Register, Zugangssperre, Drachenkill | Hauptwelt | bleiben (Schreine stehen in der Hauptwelt) |

**Optional: Spieler „von null“** mit `/sift admin reset-players`:
1. In der `config.yml` `admin.reset-players.enabled: true` setzen und neu starten. Recht: `thesift.admin.reset` (OP
   standardmäßig; nicht in `thesift.admin` enthalten), z. B. `lp user <spieler> permission set thesift.admin.reset true`.
2. Backup der Spielerdaten: `cp -a <welt>/playerdata <welt>/advancements ../sift-backups/` (vor Schritt 3).
3. Ausführen:
   ```
   sift admin reset-players
   sift admin reset-players confirm
   ```
   Erwartet: „Reset 1 ausgelöst: … Spieler online zurückgesetzt“, im Log je Spieler
   `Sift player reset 1 for <name>: … data entries, … advancements, … Sift items removed`.
   Der Reset betrifft je Spieler:
   - alle `the_sift:`-Daten im Spieler (Intro-Flag, Siftit-Sicherungen, Rückkehrpunkte);
   - alle `the_sift:`-Advancements;
   - alle Sift-Gegenstände in Inventar, Endertruhe und in Shulkerkisten darin.
4. Offline-Spieler werden beim nächsten Login zurückgesetzt, **solange `enabled: true` bleibt**. Den Schalter daher
   erst nach der ersten Spielwoche (oder wenn alle einmal da waren) wieder auf `false` stellen.
5. Nicht erfasst: Sift-Gegenstände in Kisten und anderen Behältern in der Welt (Hauptwelt, Nether, End), in Bündeln
   und bei Händlern. Diese bei Bedarf mit CoreProtect/WorldEdit oder von Hand entfernen.

Ohne den Befehl, einzeln für Spieler, die online sind:
```
advancement revoke <spieler> from the_sift:the_sift/root
advancement revoke <spieler> from the_sift:story/fallen_civilization
sift siftite list <spieler>
```
(Die Siftit-Sicherung und das Intro-Flag lassen sich nur mit dem Reset-Befehl oder einem NBT-Editor leeren.)

---

## 3. Config-Abgleich (Produktivserver)

Eine vorhandene `config.yml` wird bei Updates **nicht** überschrieben; neue Abschnitte fehlen dann und gelten mit
ihren Standardwerten. Vor dem Start mit der aktuellen Vorlage (Plugin-Jar → `config.yml`, oder `plugins/TheSift/
config.yml` umbenennen und vom Plugin neu schreiben lassen) vergleichen:

```bash
cp -a plugins/TheSift/config.yml plugins/TheSift/config.yml.preN
unzip -p plugins/TheSift-<version>.jar config.yml > /tmp/sift-config-default.yml
diff -u plugins/TheSift/config.yml /tmp/sift-config-default.yml | less
```

| Schlüssel | Empfehlung für den Start | Warum |
|---|---|---|
| `access.mode` | `always`, oder `after-dragon` mit `delay-days`, oder `fixed-date` mit `date` | Zeitpunkt der Öffnung; `sift access status` prüft das Ergebnis |
| `portals.melody`, `portals.ancient-city.melody`, `portals.ancient-city.book` | **löschen** (seit alpha.37 ohne Wirkung; sonst Warnung beim Start) | Portale öffnen nur noch mit dem Schlüssel |
| `portals.recipes.*` | Standard (gestaffelt Hauptwelt < Nether < End). Unveränderte alte Rezepte ersetzt das Plugin beim ersten Start mit alpha.37 selbst (Log „old defaults replaced“); selbst geänderte bleiben und müssen von Hand angepasst werden | Schlüssel-Staffelung |
| `portals.keys.*.loot.chance` | Hauptwelt/Nether `0.03`, End `0.0`, Antike Stadt `0.01` | sonst umgeht die Truhe das schwere Rezept |
| `portals.keys.consume` | `true` | ein Schlüssel öffnet ein Portal |
| `phase4.boss-items.*` | Standard; bei Wither-Farmen `wither-core.chance` senken oder `cooldown-hours` setzen | Schutz vor Farmen |
| `worldgen.abandoned-portals.chance` | `1.0` | Häufigkeit wie im Original (#15, alte Configs haben `0.25`) |
| `resource-pack.required` | `true`, wenn ohne Pack nicht gespielt werden soll (Sift-Blöcke sehen sonst wie Pilzblöcke aus) | `false` bietet das Pack nur an |
| `resource-pack.url` / `sha1` | URL des veröffentlichten Releases; `sha1` leer lassen (wird beim Start berechnet) | |
| `admin.reset-players.enabled` | nur für den Reset `true` (siehe Abschnitt 2) | Schutz |
| `atmosphere.*`, `weather.*`, `worldgen.*` | Standard | getestet |

Rechte (LuckPerms):
```
lp group admin permission set thesift.admin true
lp group admin permission set thesift.access.bypass true
lp group moderator permission set thesift.admin.items true
```
`thesift.access.bypass` (Betreten trotz geschlossener Sift) und `thesift.admin.reset` sind **nicht** in
`thesift.admin` enthalten und werden gezielt vergeben.

Kontrolle nach dem Start:
```
sift access status
sift pack reload
sift debug selftest
```
Erwartet: Modus und offen/zu wie gewollt; Pack-URL erreichbar, SHA-1 berechnet; Selbsttest ohne Fehler (u. a.
„ancient_resonator: Item ohne Rezept“). Im Log keine Zeile „recipe … in config.yml“ (sonst ist ein Rezept ungültig
und das eingebaute wird benutzt) und keine Warnung „no longer used“.

**Portale aus den Spieltests (seit alpha.37):** Hauptwelt, Nether und End werden nicht zurückgesetzt; dort geweckte
oder geöffnete Portale bleiben so. Zum Go-live alle prüfen und die aus den Spieltests wieder schlafen legen:
```
execute in minecraft:overworld run sift portal list
execute in minecraft:overworld run sift portal lock <id>
execute in minecraft:the_nether run sift portal lock <id>
execute in minecraft:the_end run sift portal lock <id>
```
`list` zeigt alle Welten mit Zustand. Alles außer `sift-main` sollte danach LOCKED sein (oder bewusst offen
bleiben). Ein UNLOCKED-Portal (Konsolen wach) öffnet der Schlüssel ohne Verbrauch; deshalb auch diese sperren.

Kompatibilität der übrigen Plugins (WorldEdit, WorldGuard, LibertyBans, AxiomPaper, TabTPS, Vulcan, TAB,
PlaceholderAPI, packetevents, InvSee++, CoreProtect) auf dem Produktivserver mit 26.3 beim Start prüfen: keine
Fehler im Log, `/plugins` alle grün.

---

## 4. Rollback-Plan

Vor dem Update anlegen (Server gestoppt):
```bash
mkdir -p ../sift-backups/N
cp -a plugins/TheSift-*.jar ../sift-backups/N/
cp -a plugins/TheSift/config.yml ../sift-backups/N/config.yml.preN
cp -a plugins/TheSift/datapack ../sift-backups/N/datapack.preN
tar czf ../sift-backups/N/world.preN.tar.gz <welt>
```
Hinweise:
- Den Ordner `plugins/TheSift/datapack` schreibt das Plugin bei jedem Start neu aus seiner Jar. Die Kopie ist nur
  zum Nachsehen; beim Rollback kommt das richtige Datapack mit der alten Jar.
- `messages.yml` und eigene Vorlagen (`plugins/TheSift/structures`) bei Bedarf mitsichern.

Rollback (Server gestoppt):
```bash
rm plugins/TheSift-<neu>.jar
cp -a ../sift-backups/N/TheSift-<alt>.jar plugins/
cp -a ../sift-backups/N/config.yml.preN plugins/TheSift/config.yml
```
- Nur Plugin-Fehler: Jar und Config zurück, Welt behalten, starten.
- Weltschaden: zusätzlich die Welt zurück (`mv <welt> <welt>.kaputt`,
  `tar xzf ../sift-backups/N/world.preN.tar.gz`). Wenn nur die Sift betroffen ist, reicht der Ordner
  `<welt>/dimensions/the_sift/the_sift` aus dem Backup bzw. `the_sift.preN`.
- Resource Pack: Die alte Jar zeigt mit `{version}` auf ihr eigenes Release; das alte Release muss dafür noch auf
  GitHub stehen. Danach `sift pack reload`.

Erwartet nach dem Rollback: Start ohne Fehler, `sift access status` wie vorher, Spieler sehen ihre Gegenstände.

---

## Abschluss

- [ ] Alle Spieltest-Issues (#11, #12, #13, #20, #25, #30) geschlossen.
- [ ] Sift zurückgesetzt (#16), bei Bedarf Spieler-Reset durchgeführt.
- [ ] Config abgeglichen, Rechte vergeben, Selbsttest grün.
- [ ] Backup und Rollback-Plan liegen bereit.
- [ ] #17 (Phase 2 und 3) geschlossen.
