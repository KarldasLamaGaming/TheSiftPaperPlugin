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
| [#20](https://github.com/KarldasLamaGaming/TheSiftPaperPlugin/issues/20) | Spieltest Portal-Öffnen an Konsolen; seit alpha.37 mit dem Schlüssel statt der Melodie (GOLIVE.md #20) | wartet auf Test |
| [#25](https://github.com/KarldasLamaGaming/TheSiftPaperPlugin/issues/25) | Spieltest Blub: Zähmen, Sitzen, Verteidigung, Rudelzorn, Basis-Wolf | offen |
| [#18](https://github.com/KarldasLamaGaming/TheSiftPaperPlugin/issues/18) | Produktionsstart: Plugin-Kompatibilität mit 26.3 (WorldEdit, WorldGuard, LibertyBans, AxiomPaper, TabTPS, Vulcan, TAB, PlaceholderAPI, packetevents, InvSee++, CoreProtect) | offen |

## Funktionen (`feature`)

| Issue | Punkt | Stand |
|---|---|---|
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
- Phase 3 ist mit alpha.32 (Wetter und Ichor-Schneefall, im PR) inhaltlich vollständig: Ichor, Rifts, Siftit-Erhalt,
  alle 18 Java-Weltgenerierungs-Features des Originals, Atmosphäre, Allays und Wetter (alpha.21–32). Was davon ohne
  Client-Mod nicht geht, steht in ABWEICHUNGEN.md → „Himmel und Atmosphäre“.
- Vor dem Produktionsstart: Schritt für Schritt in `docs/GOLIVE.md` (Spieltests, Welt- und Spieler-Reset,
  Config-Abgleich, Rollback). Kurzfassung: die Spieltests #11, #12, #13, #20, #25 abschließen, #30 (Item-Displays beim
  Chunk-Entladen) nach den Hotfixes prüfen und schließen, Plugin-Kompatibilität (#18) auf dem Produktivserver
  prüfen, die Sift einmal zurücksetzen (#16, TESTING → „Sift-Welt zurücksetzen“), bestehende `config.yml` mit den
  neuen Standardwerten abgleichen (u. a. `abandoned-portals.chance: 1.0`), #17 schließen.
- Bis zum Go-live: alpha.42f Boss-Balance und ein Altar je Spieler → alpha.42g Sift-Blöcke ohne Aufblitzen des
  Vanilla-Pilzblocks beim Platzieren (alpha.42d und 42e sind gemergt) → gesammelter Playtest → Welt- und Spieler-Reset (GOLIVE.md) → Go-live als v0.1.0-beta.1. Danach optional
  Prüfungsräume für Wächter und Wither (Option B). Name des Golem-Items: Nachtkristall (Id `golem_trophy` bleibt); der Charoit-Kern behält seinen
  Namen.
- Bedrock-Optik für Sift-Blöcke (auch den Sculk Enchanter) bräuchte Geyser-Mappings und ein Bedrock-Pack; bisher
  sehen Bedrock-Spieler die Ersatzblöcke (Pilzblöcke), die Funktion bleibt gleich.
- Boss-Items aus Phase 4 (Herz des Wächters, Drachenschuppe, Kern des Withers) lassen sich später ohne neuen PR als
  Zutaten in `portals.recipes` eintragen (`the_sift:<id>`); ein ungültiges Rezept fällt auf das eingebaute zurück.
- Schritt 6a: Seelenschluchten fertig (alpha.18/18.1).
- Schritt 6b: Echo Golem (alpha.19, im PR), Spieltest siehe `docs/TESTING.md` → Mobs: Echo Golem. Danach ist
  Phase 2 vollständig.
