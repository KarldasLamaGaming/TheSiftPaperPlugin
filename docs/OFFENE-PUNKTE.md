# Offene Punkte

Übersicht der offenen Punkte; Details, Diskussion und Ergebnisse stehen jeweils im GitHub-Issue.
Erledigte Punkte werden hier gestrichen, sobald das Issue geschlossen ist.

Stand: 31.10.2026 (nach alpha.10)

## Fehler (`bug`)

| Issue | Punkt | Stand |
|---|---|---|
| [#10](https://github.com/KarldasLamaGaming/TheSiftPaperPlugin/issues/10) | Ichorglasscheibe ist eine einfache Vanilla-Scheibe: Schutz und Rezept klären | Entscheidung offen |
| [#11](https://github.com/KarldasLamaGaming/TheSiftPaperPlugin/issues/11) | Weidenlaub: Härte 0,7 (config) vs. 0,2 (Plan) | Original geprüft: 0,7 ist richtig; Spieltest offen, dann schließen |
| [#16](https://github.com/KarldasLamaGaming/TheSiftPaperPlugin/issues/16) | Alte Chunks (alpha.2–4): keine Bäume, Lotus-Reste | Testserver-Sift zurücksetzen; Produktion startet frisch |

## Tests (`test`)

| Issue | Punkt | Stand |
|---|---|---|
| [#12](https://github.com/KarldasLamaGaming/TheSiftPaperPlugin/issues/12) | Rechte-Test `thesift.admin.items` ohne OP | offen |
| [#13](https://github.com/KarldasLamaGaming/TheSiftPaperPlugin/issues/13) | Spielertests: Amboss, Schleifstein, Händler, Schallplatten, Entrinden mit Axt, Ichorglas „Schnell“, Feuer-Ausbreitung | offen |
| [#20](https://github.com/KarldasLamaGaming/TheSiftPaperPlugin/issues/20) | Spieltest Portal-Melodie an Konsolen (nach dem Platzier-Fix in 7b) | wartet auf Test |
| [#18](https://github.com/KarldasLamaGaming/TheSiftPaperPlugin/issues/18) | Produktionsstart: Plugin-Kompatibilität mit 26.3 (WorldEdit, WorldGuard, LibertyBans, AxiomPaper, TabTPS, Vulcan, TAB, PlaceholderAPI, packetevents, InvSee++, CoreProtect) | offen |

## Funktionen (`feature`)

| Issue | Punkt | Stand |
|---|---|---|
| [#14](https://github.com/KarldasLamaGaming/TheSiftPaperPlugin/issues/14) | Weidenbretter nur in eigenen Rezepten (Truhe, Fass, Holzwerkzeuge usw. fehlen) | Vorschlag im Issue |
| [#15](https://github.com/KarldasLamaGaming/TheSiftPaperPlugin/issues/15) | Verlassene Portale: `chance` 0,25 mit Phase 3 auf 1,0 | wartet auf Phase 3 |
| [#17](https://github.com/KarldasLamaGaming/TheSiftPaperPlugin/issues/17) | Phase 2 (Mobs) und Phase 3 (Ichor, Himmel, Monolithen, Bögen, Seelenschluchten, Sniffer-Höhlen) | nach Phase 1 |

## Nächster Schritt

- Phase 2: Mob-Framework und Blub sind im PR. Spieltest siehe `docs/TESTING.md` → Mobs: Blub.
- Danach je ein PR: Sifter, Singer mit Antiker Stadt, Dunkler Schnüffler, Echogolem.
