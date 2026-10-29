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

## Weidenholz

Weidenholz und -laub brennen nicht und taugen nicht als Brennstoff (Pilzblock-Zustände). Wird mit
Schritt 6 gegen das Original geprüft und angeglichen.
