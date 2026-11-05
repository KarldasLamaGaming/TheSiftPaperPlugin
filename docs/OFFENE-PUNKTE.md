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
| [#15](https://github.com/KarldasLamaGaming/TheSiftPaperPlugin/issues/15) | Verlassene Portale: `chance` 0,25 mit Phase 3 auf 1,0 | alpha.28: auf 1,0 gesetzt (gleiche 52 Kandidaten wie im Original: je 8 gebaut, 15 %). **Nach alpha.30 (letzte Worldgen-Features) erneut nachmessen, erst dann schließen.** |
| [#17](https://github.com/KarldasLamaGaming/TheSiftPaperPlugin/issues/17) | Phase 2 (Mobs) und Phase 3 (Ichor, Himmel, Monolithen, Bögen, Seelenschluchten, Sniffer-Höhlen) | nach Phase 1 |

## Phase 4 – Ideen (nach dem Original-Port)

Nur Ideensammlung, noch nicht geplant oder umgesetzt. Reihenfolge: zuerst die Spezial-Items und der
Enchanter-Block (3 und 2), danach der Boss mit Tempel (1).

1. **Boss „Großer Echo Golem“**
   - Riesiger Golem mit Seelenblöcken als Panzer. Die Spieler nehmen ihm die Blöcke ab; nur ohne Blöcke nimmt er
     Schaden. Mehrere Phasen.
   - Ort: zerfallener Tempel (Struktur aus dem Populator).
   - Beute: verzauberte Netherit-Rüstung (ähnlich der Diamant-Ausrüstung in den End-Städten); die Spieler dürfen
     die abgenommenen Seelenblöcke behalten; dazu ein Spezial-Item.
   - Offen: Skalierung für mehrere Spieler, Zurücksetzen der Arena, Display-Grenzen für den Boss, Beute je Spieler.
2. **Boss-Drops**
   - Wärter (Warden) → „Herz des Wärters“
   - Enderdrache → „Drachenschuppe“
   - Wither → Spezial-Item (Arbeitstitel „Kern des Withers“)
   - Großer Echo Golem → Spezial-Item (Name offen)
   - Offen: feste Menge je Kill, Schutz vor Farmen (Wither- und Wärter-Farmen).
3. **Charoite und „Sculk Enchanter“**
   - Eigener Block. Er steht nur im Boss-Tempel und ist craftbar, aber sehr aufwendig.
   - Erlaubt Verzauberungen bis 2 Stufen über dem Vanilla-Maximum (z. B. Haltbarkeit V, Schärfe VII).
     Ausnahme: einstufige Verzauberungen (Reparatur usw.).
   - Zutaten: Charoite, je ein Item der drei Vanilla-Bosse, Siftit, Erfahrungslevel.
   - Offen: Bedienung des Menüs, Whitelist in der Config, Grenze je Gegenstand, Balance im PvP.

## Nächster Schritt

- Phase 2: Blub ist fertig (alpha.12), offene Spielertests in #25. Sifter fertig (alpha.13). Singer und Antike Stadt
  fertig (alpha.14). Dunkler Schnüffler im PR, Spieltest siehe `docs/TESTING.md` → Mobs: Dunkler Schnüffler.
- Phase 3: Ichor, Rifts, Siftit-Erhalt, Seen, Quellen, Kaskaden und Ichor-Schnee fertig (alpha.21–26).
  Monolithen und Bögen fertig (alpha.27). Schnüffler-Höhlen im PR (alpha.28), Spieltest siehe
  `docs/TESTING.md` → Schnüffler-Höhlen. Danach Atmosphäre (alpha.29) und die letzten Worldgen-Features (alpha.30);
  nach alpha.30 die Portal-Häufigkeit für #15 erneut nachmessen.
- Schritt 6a: Seelenschluchten fertig (alpha.18/18.1).
- Schritt 6b: Echo Golem (alpha.19, im PR), Spieltest siehe `docs/TESTING.md` → Mobs: Echo Golem. Danach ist
  Phase 2 vollständig.
