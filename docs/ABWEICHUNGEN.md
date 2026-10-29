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

Sift-Items bauen auf Vanilla-Items auf (Papier, Netherit, Schneeball). Alle geschützten Items (siehe
`docs/ADMIN.md`, Schutz) verhalten sich nicht wie ihre Basis. Im Original gibt es diese Frage nicht,
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
  Datapack-Features, nicht dazwischen.
- Das Haupt-Portal wird beim ersten Start gebaut, nachdem die Chunks um 0/0 generiert sind (das Original
  baut es beim Serverstart vor der Generierung). Bäume und Pflanzen auf dem Bauplatz werden dabei entfernt;
  verlassene Portale halten sich vom Suchgebiet fern.
- Die Dunklen Schnüffler in Sculk-Regionen kommen mit den Mobs (Phase 2).
- Monolithen, Bögen, Seelenschluchten, Schnüffler-Höhlen, Ichor-Seen und -Quellen, Lavaböden und
  Ichorschnee kommen mit Phase 3.
