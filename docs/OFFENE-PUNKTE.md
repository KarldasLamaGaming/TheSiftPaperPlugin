# Offene Punkte

Übersicht der offenen Punkte; Details, Diskussion und Ergebnisse stehen jeweils im GitHub-Issue.
Erledigte Punkte werden hier gestrichen, sobald das Issue geschlossen ist.

Stand: 31.10.2026 (nach alpha.10)

## Fehler (`bug`)

| Issue | Punkt | Stand |
|---|---|---|
| [#11](https://github.com/KarldasLamaGaming/TheSiftPaperPlugin/issues/11) | Weidenlaub: Härte 0,7 (config) vs. 0,2 (Plan) | Original geprüft: 0,7 ist richtig; Spieltest offen, dann schließen |
| [#16](https://github.com/KarldasLamaGaming/TheSiftPaperPlugin/issues/16) | Alte Chunks (alpha.2–4): keine Bäume, Lotus-Reste | Nur Doku (ADMIN „Alte Chunks“, seit alpha.25): Sift vor dem Produktionsstart zurücksetzen |

## Tests (`test`)

| Issue | Punkt | Stand |
|---|---|---|
| [#12](https://github.com/KarldasLamaGaming/TheSiftPaperPlugin/issues/12) | Rechte-Test `thesift.admin.items` ohne OP | offen |
| [#13](https://github.com/KarldasLamaGaming/TheSiftPaperPlugin/issues/13) | Spielertests: Amboss, Schleifstein, Händler, Schallplatten, Entrinden mit Axt, Ichorglas „Schnell“, Feuer-Ausbreitung | offen |
| [#20](https://github.com/KarldasLamaGaming/TheSiftPaperPlugin/issues/20) | Spieltest Portal-Melodie an Konsolen (nach dem Platzier-Fix in 7b) | wartet auf Test |
| [#25](https://github.com/KarldasLamaGaming/TheSiftPaperPlugin/issues/25) | Spieltest Blub: Zähmen, Sitzen, Verteidigung, Rudelzorn, Basis-Wolf | offen |
| [#18](https://github.com/KarldasLamaGaming/TheSiftPaperPlugin/issues/18) | Produktionsstart: Plugin-Kompatibilität mit 26.3 (WorldEdit, WorldGuard, LibertyBans, AxiomPaper, TabTPS, Vulcan, TAB, PlaceholderAPI, packetevents, InvSee++, CoreProtect) | offen |

## Funktionen (`feature`)

| Issue | Punkt | Stand |
|---|---|---|
| [#15](https://github.com/KarldasLamaGaming/TheSiftPaperPlugin/issues/15) | Verlassene Portale: `chance` 0,25 mit Phase 3 auf 1,0 | wartet auf Phase 3 |
| [#17](https://github.com/KarldasLamaGaming/TheSiftPaperPlugin/issues/17) | Phase 2 (Mobs) und Phase 3 (Ichor, Himmel, Monolithen, Bögen, Seelenschluchten, Sniffer-Höhlen) | nach Phase 1 |

## Phase 3, offen

| Punkt | Stand |
|---|---|
| Ichor-Kaskaden (zwei Seen mit Wasserfall an Steilhängen) und Weideninseln in großen überwucherten Seen | Folge-PR nach alpha.25 |

## Nächster Schritt

- Phase 2: Blub ist fertig (alpha.12), offene Spielertests in #25. Sifter fertig (alpha.13). Singer und Antike Stadt
  fertig (alpha.14). Dunkler Schnüffler im PR, Spieltest siehe `docs/TESTING.md` → Mobs: Dunkler Schnüffler.
- Schritt 6a: Seelenschluchten fertig (alpha.18/18.1).
- Schritt 6b: Echo Golem (alpha.19, im PR), Spieltest siehe `docs/TESTING.md` → Mobs: Echo Golem. Danach ist
  Phase 2 vollständig.
