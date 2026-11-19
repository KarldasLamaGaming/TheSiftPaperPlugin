# Offene Punkte

Übersicht der offenen Punkte; Details, Diskussion und Ergebnisse stehen jeweils im GitHub-Issue.
Erledigte Punkte werden hier gestrichen, sobald das Issue geschlossen ist.

Stand: 13.11.2026 (alpha.42f und 42g gemergt, alpha.42h im PR #79, danach alpha.42i)

## Fehler (`bug`) und Funktionen (`feature`)

Keine offenen Issues (Stand 13.11.). Neue Funde bitte als Issue anlegen.

## Tests (`test`)

Keine offenen Test-Issues. Die alten Spieltest-Issues (#11, #12, #13, #20, #25) und #30 sind seit dem 09.11.
geschlossen, #18 (Plugin-Kompatibilität) seit dem 03.11. Was davon noch nicht im Spiel geprüft ist, steht als
„Nur mit Spieler“-Liste in `docs/TESTING.md` und gehört in den **gesammelten Playtest**.

## Geschlossen (zur Nachverfolgung)

| Issue | Punkt | Wie erledigt |
|---|---|---|
| #76 | Sift-Blöcke blitzen beim Platzieren als Vanilla-Pilz auf | alpha.42g |
| #77 | Boss-Balance und ein Altar je Spieler | alpha.42f |
| #10 | Ichorglasscheibe als Vanilla-Scheibe | bleibt Vanilla, Doku (ABWEICHUNGEN) |
| #14 | Weidenbretter in allen Holzrezepten | alpha.24 |
| #15 | Verlassene Portale `chance` 1,0 | alpha.30 |
| #16 | Alte Chunks ohne Bäume | Doku; die Sift wird vor beta.1 zurückgesetzt (GOLIVE.md) |
| #17 | Phase 2 und Phase 3 | vollständig (alpha.11–32) |
| #30 | Item-Displays beim Chunk-Entladen | alpha.17 |

## Phase 4 – eigene Inhalte (umgesetzt)

Karls Ideen nach dem Port des Originals, alle unter `phase4.*` abschaltbar:
- **Boss-Items** (alpha.39): Herz des Wächters, Drachenschuppe, Kern des Withers, Nachtkristall (`golem_trophy`).
- **Charoit-Kern und Sculk Enchanter** (alpha.40): Verzauberungen bis 2 Stufen über dem Vanilla-Maximum.
- **Tempel** (alpha.41a, Bastion alpha.41.1, Kompass und Gerüchte-Zettel alpha.41b): 10 je Sift, Wege für 3 × 3 × 3
  seit alpha.42e (Layout-Version 4, `/sift temple validate`).
- **Brut-Spawner** (alpha.42a): 12 je Tempel in drei Zonen.
- **Großer Echo-Golem** (alpha.42b): Phasen Panzer, Schall, Wut, Seelengitter, Netherit-Beute, Advancement; Balance und
  „ein Altar je Spieler“ mit alpha.42f.
- **Zweisprachig** (alpha.42c): Pack und Meldungen auf Deutsch und Englisch.
- **Charoit-Irrlicht** (alpha.42d): fliegender Fernkämpfer in Zone 3.

## Nächster Schritt

- **Bis zum Go-live:** alpha.42f, 42g, 42h (gemergt bzw. im Merge) → alpha.42i (Erfahrung der Sift-Mobs, zwei
  Advancements unsichtbar) → gesammelter Playtest (alle „Nur mit Spieler“-Listen in
  `docs/TESTING.md`) → Welt- und Spieler-Reset (`docs/GOLIVE.md`) → Go-live als v0.1.0-beta.1.
- **Vor dem Produktionsstart** (GOLIVE.md): bestehende `config.yml` mit den neuen Standardwerten abgleichen (die
  Migrationen ersetzen nur unveränderte alte Werte), offene Entscheidungen `access.mode`, Datum, Pack-Pflicht.
- **Nach beta.1:** Anbau der Sculkblume. Die Samen gibt es schon (der Dunkle Schnüffler gräbt sie aus); es fehlen Setzen auf
  Sculk, Wachstum und Ernte. Bis dahin ist das Advancement „Das Dunkel pflanzen“ unsichtbar (alpha.42i), ebenso
  „Rissgänger“, solange `rifts.enabled: false` ist.
- **Nach beta.1, optional:** Prüfungsräume für Wächter und Wither (Option B; die Kammern sind in den Tempeln schon
  versiegelt vorhanden).
- **Bekannte Grenzen:** Bedrock-Optik für Sift-Blöcke (auch den Sculk Enchanter) bräuchte Geyser-Mappings und ein
  Bedrock-Pack; bisher sehen Bedrock-Spieler die Ersatzblöcke (Pilzblöcke), die Funktion bleibt gleich. Boss-Items
  lassen sich ohne neuen PR als Zutaten in `portals.recipes` eintragen (`the_sift:<id>`).
