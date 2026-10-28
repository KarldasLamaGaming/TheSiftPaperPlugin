# The Sift – Admin-Handbuch

Stand: Phase 1, Schritte 1–3 (Dimension, Datapack, Bootstrapper). Wird mit jeder Phase ergänzt.

## Installation

1. `TheSift-<version>.jar` nach `plugins/` legen (Paper 26.3, Java 25).
2. Server starten. Das Plugin installiert sein Datapack nach `plugins/TheSift/datapack/`
   und meldet es bei Paper an (`/datapack list` zeigt `TheSift/the_sift`).
3. Die Sift wird automatisch als eigene Welt geladen:
   - Key `the_sift:the_sift`, Bukkit-Name `<level-name>_the_sift_the_sift`
   - Ordner `<level-name>/dimensions/the_sift/the_sift/`
4. **Beim allerersten Start einmal neu starten** (siehe unten). Das Log sagt es mit `RESTART`.

## Paper-Welt-Config der Sift

Die eigenen Blöcke der Sift nutzen sonst ungenutzte Zustände von Pilzblöcken, Stolperdraht und
Chorus-Pflanzen. Vanilla würde diese Zustände bei Nachbar-Updates verändern, daher braucht die
Sift-Welt in ihrer `paper-world.yml`:

```yaml
block-updates:
  disable-mushroom-block-updates: true
  disable-tripwire-updates: true
  disable-chorus-plant-updates: true
```

- Die Schalter gehören zur **Welt-Config** (Paper `WorldConfiguration.BlockUpdates`), nicht zu
  `paper-global.yml`.
- Das Plugin setzt sie selbst in
  `<level-name>/dimensions/the_sift/the_sift/paper-world.yml` (abschaltbar mit
  `server-config.manage-sift-world-config: false`, dann wird nur gewarnt).
- Paper liest die Welt-Config beim Anlegen der Welt. Existiert die Sift-Welt noch nicht, darf das
  Plugin den Ordner vorher nicht anlegen (Paper hielte die Welt sonst für vorhanden und bricht ab).
  Deshalb schreibt es die Datei beim ersten Start direkt nach dem Anlegen; wirksam wird sie nach
  einem Neustart. Bis dahin sollte niemand die Sift betreten.

### Nebenwirkungen – nicht in `paper-world-defaults.yml` setzen

In einer Welt mit diesen Schaltern:

- verbinden sich Stolperdrähte nicht und lösen keine Fallen aus,
- verbinden sich Chorus-Pflanzen nicht und brechen nicht ab, Chorus-Farmen funktionieren nicht,
- passen Pilzblöcke ihre Seiten nicht an Nachbarn an.

Deshalb nur für die Sift-Welt setzen. Die Option `custom-blocks.place-only-in-sift` (kommt mit den
Custom-Blöcken in Phase 1, Schritt 4; Standard `true`) erlaubt das Platzieren von Custom-Blöcken nur in
der Sift, damit keine andere Welt die Schalter braucht.

Bekannte Einschränkung: Spieler können in anderen Welten (mit Updates) Pilzblöcke so stapeln, dass
Vanilla genau einen der verwendeten Zustände erzeugt (z. B. der obere von zwei gestapelten
Pilzstielen). Mit dem Resource Pack sieht dieser Block dann wie ein Sift-Block aus. Er verhält sich
weiter wie ein Pilzblock.

## Befehle (`thesift.admin`)

| Befehl | Wirkung |
|---|---|
| `/sift tp` | In die Sift bzw. zurück in die Hauptwelt |
| `/sift debug populator` | Zähler des Test-Populators |
| `/sift debug scan [radius] [chunkX chunkZ]` | Lädt/generiert Chunks der Sift und zählt die Ersatz-Blockzustände |

## Test-Populator

`debug.populator-marker: true` setzt in jedem neu generierten Sift-Chunk einen Glowstone-Block auf die
höchste Stelle der Chunkmitte. Nur für Tests, nie auf dem Produktivserver.
